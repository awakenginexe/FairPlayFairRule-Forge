package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.config.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded asynchronous Discord notifications with direct manifest attachments. */
public final class DiscordWebhookService {
    private static final Duration HASTEBIN_WAIT = Duration.ofMillis(2500);
    private static RuntimeState runtime;

    private DiscordWebhookService() {
    }

    public static void sendPlayerJoinNotification(ServerPlayer player) {
        URI webhook = configuredWebhook();
        if (webhook == null) {
            return;
        }
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        Instant timestamp = Instant.now();
        submit(snapshot.playerId(), runtime().delivery.deliverNotification(
                webhook, joinSummary(snapshot, timestamp)), "join notification");
    }

    public static void sendPlayerManifest(ServerPlayer player, List<String> mods, List<String> packs,
                                          int packEnabledCount, int packDisabledCount) {
        URI webhook = configuredWebhook();
        if (webhook == null) {
            return;
        }
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        List<String> immutableMods = List.copyOf(mods);
        List<String> immutablePacks = List.copyOf(packs);
        Instant timestamp = Instant.now();
        DiscordManifestDeliveryService.ManifestDeliveryRequest request =
                DiscordManifestDeliveryService.ManifestDeliveryRequest.generated(
                        webhook,
                        snapshot.playerId(),
                        timestamp,
                        () -> DiscordManifestTextBuilder.playerManifest(
                                immutableMods, immutablePacks),
                        manifestSummary(snapshot, immutableMods.size(),
                                packEnabledCount, packDisabledCount),
                        Config.HASTEBIN_MIRROR_ENABLED.get());
        submit(snapshot.playerId(), runtime().delivery.deliver(request), "player manifest");
    }

    public static void sendBanNotification(ServerPlayer player, String bannedModId,
                                           List<String> mods, List<String> packs) {
        URI webhook = configuredWebhook();
        if (webhook == null) {
            return;
        }
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        List<String> immutableMods = List.copyOf(mods);
        List<String> immutablePacks = List.copyOf(packs);
        Instant timestamp = Instant.now();
        DiscordManifestDeliveryService.ManifestDeliveryRequest request =
                DiscordManifestDeliveryService.ManifestDeliveryRequest.generated(
                        webhook,
                        snapshot.playerId(),
                        timestamp,
                        () -> DiscordManifestTextBuilder.banManifest(
                                bannedModId, immutableMods, immutablePacks),
                        banSummary(snapshot, bannedModId, timestamp),
                        Config.HASTEBIN_MIRROR_ENABLED.get());
        submit(snapshot.playerId(), runtime().delivery.deliver(request), "ban notification");
    }

    /** Called from the Forge server-stop event so no webhook worker survives its server. */
    public static synchronized void shutdown() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    private static void submit(UUID playerId,
                               CompletableFuture<DiscordManifestDeliveryService.DeliveryResult> future,
                               String notificationType) {
        future.whenComplete((result, failure) -> {
            if (failure != null) {
                FairPlayFairRule.LOGGER.error(
                        "Discord {} failed for UUID {}: asynchronous transport failure",
                        notificationType, playerId);
            } else if (!result.success()) {
                FairPlayFairRule.LOGGER.error(
                        "Discord {} failed for UUID {}: {}",
                        notificationType, playerId, result.code());
            } else {
                FairPlayFairRule.LOGGER.info(
                        "Sent Discord {} for UUID {}", notificationType, playerId);
            }
        });
    }

    private static URI configuredWebhook() {
        String configured = Config.WEBHOOK_URL.get();
        if (configured == null || configured.isBlank()) {
            FairPlayFairRule.LOGGER.debug("Webhook URL not configured, skipping Discord notification");
            return null;
        }
        try {
            URI uri = URI.create(configured);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Webhook URI must be HTTP(S)");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            FairPlayFairRule.LOGGER.error(
                    "Discord webhook configuration is invalid; notification skipped");
            return null;
        }
    }

    private static synchronized RuntimeState runtime() {
        if (runtime == null) {
            runtime = new RuntimeState();
        }
        return runtime;
    }

    private static JsonObject joinSummary(PlayerSnapshot player, Instant timestamp) {
        JsonObject embed = baseEmbed("🟢 Player Joined Server", 3066993,
                "FairPlayFairRule");
        JsonArray fields = embed.getAsJsonArray("fields");
        fields.add(field("Player", player.displayName(), true));
        fields.add(field("UUID", '`' + player.playerId().toString() + '`', true));
        fields.add(field("Timestamp", timestamp.toString(), false));
        return payload(embed);
    }

    private static JsonObject manifestSummary(PlayerSnapshot player, int modCount,
                                              int enabledPacks, int disabledPacks) {
        JsonObject embed = baseEmbed("📋 Player Manifest", 3447003,
                "FairPlayFairRule");
        JsonArray fields = embed.getAsJsonArray("fields");
        fields.add(field("Player", player.displayName(), true));
        fields.add(field("UUID", '`' + player.playerId().toString() + '`', true));
        fields.add(field("Mods (Enabled)", Integer.toString(modCount), true));
        fields.add(field("Resource Packs (Enabled)", Integer.toString(enabledPacks), true));
        fields.add(field("Resource Packs (Disabled)", Integer.toString(disabledPacks), true));
        return payload(embed);
    }

    private static JsonObject banSummary(PlayerSnapshot player, String bannedModId,
                                         Instant timestamp) {
        JsonObject embed = baseEmbed("🚫 PLAYER BANNED - PROHIBITED MOD DETECTED",
                15158332, "FairPlayFairRule - HIGH PRIORITY ALERT");
        JsonArray fields = embed.getAsJsonArray("fields");
        fields.add(field("Player", player.displayName(), true));
        fields.add(field("UUID", '`' + player.playerId().toString() + '`', true));
        fields.add(field("Banned Mod", '`' + Objects.requireNonNull(bannedModId) + '`', false));
        fields.add(field("Timestamp", timestamp.toString(), false));
        return payload(embed);
    }

    private static JsonObject baseEmbed(String title, int color, String footerText) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.add("fields", new JsonArray());
        JsonObject footer = new JsonObject();
        footer.addProperty("text", footerText);
        embed.add("footer", footer);
        return embed;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private static JsonObject payload(JsonObject embed) {
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        return payload;
    }

    private record PlayerSnapshot(UUID playerId, String displayName) {
        private static PlayerSnapshot capture(ServerPlayer player) {
            return new PlayerSnapshot(player.getUUID(), player.getName().getString());
        }
    }

    private static final class RuntimeState implements AutoCloseable {
        private final ThreadPoolExecutor deliveryExecutor = boundedExecutor(
                "fpfr-discord-delivery", 2, 32);
        private final ThreadPoolExecutor httpExecutor = boundedExecutor(
                "fpfr-discord-http", 4, 64);
        private final DiscordManifestDeliveryService delivery;

        private RuntimeState() {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .executor(httpExecutor)
                    .build();
            delivery = new DiscordManifestDeliveryService(
                    new ManifestAttachmentPlanner(),
                    new JdkBoundedHttpTransport(client),
                    deliveryExecutor,
                    HASTEBIN_WAIT,
                    () -> "fpfr" + UUID.randomUUID().toString().replace("-", ""));
        }

        @Override
        public void close() {
            deliveryExecutor.shutdownNow();
            httpExecutor.shutdownNow();
        }
    }

    private static ThreadPoolExecutor boundedExecutor(String name, int threads, int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, name + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
