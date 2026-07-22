package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.network.ClientInfoPayload;
import com.example.fairplayfairrule.network.PacketHandler;
import com.example.fairplayfairrule.network.ResourcePackReportType;
import com.example.fairplayfairrule.resourcepack.ResourcePackHashService;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Event-driven client collection with serialized off-thread ZIP hashing. */
public final class ClientDataService {
    private static final ResourcePackManifestService MANIFEST_SERVICE =
            new ResourcePackManifestService(new ResourcePackHashService());
    private static ThreadPoolExecutor hashExecutor;
    private static CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

    private ClientDataService() {
    }

    /** Called only by join and resource-reload completion events. */
    public static void collectAndSendData(ResourcePackReportType reportType) {
        Object connectionIdentity = Minecraft.getInstance().getConnection();
        if (connectionIdentity == null) {
            FairPlayFairRule.LOGGER.warn("Cannot collect a client integrity report before connection setup");
            return;
        }
        ClientConnectionGuard connectionGuard = new ClientConnectionGuard(connectionIdentity);
        List<String> mods = ModList.get().getMods().stream()
                .map(modInfo -> modInfo.getModId() + "@" + modInfo.getVersion())
                .collect(Collectors.toCollection(ArrayList::new));
        List<ResolvedResourcePack> selected;
        try {
            selected = MANIFEST_SERVICE.resolveSelectedPacks();
        } catch (RuntimeException exception) {
            FairPlayFairRule.LOGGER.error("Unable to resolve the active resource-pack selection");
            selected = List.of(new ResolvedResourcePack(
                    "Active resource pack could not be resolved",
                    com.example.fairplayfairrule.resourcepack.ResourcePackType.UNRESOLVED, null));
        }
        enqueueReport(reportType, List.copyOf(mods), selected, connectionGuard);
    }

    private static synchronized void enqueueReport(ResourcePackReportType reportType,
                                                   List<String> mods,
                                                   List<ResolvedResourcePack> selected,
                                                   ClientConnectionGuard connectionGuard) {
        try {
            sendChain = sendChain.handle((ignored, failure) -> null).thenRunAsync(() -> {
            List<ResourcePackManifestEntry> manifest = MANIFEST_SERVICE.buildManifest(selected);
            ClientInfoPayload payload;
            try {
                payload = new ClientInfoPayload(reportType, mods, manifest);
            } catch (IllegalArgumentException exception) {
                FairPlayFairRule.LOGGER.error("Client manifest exceeded protocol safety bounds");
                return;
            }

            Minecraft.getInstance().execute(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null && connectionGuard.matches(minecraft.getConnection())) {
                    ClientPacketSender.sendToServer(new PacketHandler.ClientInfoPacket(payload));
                    FairPlayFairRule.LOGGER.info("Sent {} client integrity report with {} active pack entries",
                            reportType, manifest.size());
                } else {
                    FairPlayFairRule.LOGGER.debug("Discarded a stale client integrity report after connection change");
                }
            });
            }, executor());
        } catch (RejectedExecutionException exception) {
            FairPlayFairRule.LOGGER.warn("Client resource-pack report queue is full; report skipped");
        }
    }

    public static synchronized void onDisconnect() {
        if (hashExecutor != null) {
            hashExecutor.shutdownNow();
            hashExecutor = null;
        }
        sendChain = CompletableFuture.completedFuture(null);
    }

    private static synchronized ThreadPoolExecutor executor() {
        if (hashExecutor == null || hashExecutor.isShutdown()) {
            hashExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(8), runnable -> {
                        Thread thread = new Thread(runnable, "fairplay-resource-pack-hasher");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
        }
        return hashExecutor;
    }
}
