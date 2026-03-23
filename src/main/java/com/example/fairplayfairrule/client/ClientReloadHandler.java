package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client-side handler for resource pack reload events
 * Re-checks and re-sends the manifest when resource packs are changed
 */
@OnlyIn(Dist.CLIENT)
public class ClientReloadHandler {

    /**
     * Register a reload listener for detecting resource pack changes
     * Called during mod initialization via RegisterClientReloadListenersEvent
     *
     * @param event The register client reload listeners event
     */
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ResourcePackReloadListener());
        FairPlayFairRule.LOGGER.info("Registered resource pack reload listener");
    }

    /**
     * Custom reload listener that triggers data re-collection when resource packs change
     */
    private static class ResourcePackReloadListener implements PreparableReloadListener {

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                              ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler,
                                              ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor,
                                              Executor gameExecutor) {

            // Use the preparation barrier to coordinate with other reload listeners
            return preparationBarrier.wait(null).thenRunAsync(() -> {
                // This runs on the game executor (main client thread)

                // Only send data if player has consented and is connected to a server
                if (!ClientScreenHandler.hasConsented) {
                    FairPlayFairRule.LOGGER.debug("Resource pack reload detected but player hasn't consented");
                    return;
                }

                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null && minecraft.getConnection() != null) {
                    FairPlayFairRule.LOGGER.info("Resource pack reload detected - re-collecting and sending data");
                    ClientDataService.collectAndSendData();
                } else {
                    FairPlayFairRule.LOGGER.debug("Resource pack reload detected but not connected to server");
                }

            }, gameExecutor);
        }
    }
}
