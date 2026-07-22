package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.resourcepack.ResourcePackViolation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Reporting-only boundary. Validation and disconnect decisions never depend on it. */
public final class ResourcePackViolationReporter implements AutoCloseable {
    public static final Duration DEDUP_WINDOW = Duration.ofSeconds(30);
    public static final int MAX_DEDUP_ENTRIES = 1_024;
    private final Supplier<ReportingConfig> configSupplier;
    private final DiscordManifestDeliveryService delivery;
    private final ThreadPoolExecutor formattingExecutor;
    private final LongSupplier clock;
    private final Map<DedupKey, Long> recent = new ConcurrentHashMap<>();

    public ResourcePackViolationReporter(Supplier<ReportingConfig> configSupplier,
                                         DiscordManifestDeliveryService delivery) {
        this(configSupplier, delivery, System::currentTimeMillis, 32);
    }

    ResourcePackViolationReporter(Supplier<ReportingConfig> configSupplier,
                                  DiscordManifestDeliveryService delivery,
                                  LongSupplier clock, int queueCapacity) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.clock = Objects.requireNonNull(clock, "clock");
        formattingExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), task -> {
                    Thread thread = new Thread(task, "fpfr-violation-format");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public ReportAdmission report(ResourcePackViolationEvent event) {
        Objects.requireNonNull(event, "event");
        ReportingConfig config;
        try { config = configSupplier.get(); }
        catch (RuntimeException exception) { return ReportAdmission.DISABLED; }
        if (config == null || !config.enabled || config.webhookUri == null) {
            return ReportAdmission.DISABLED;
        }
        long now = clock.getAsLong();
        DedupKey key = DedupKey.from(event);
        if (duplicate(key, now)) return ReportAdmission.DEDUPLICATED;
        try {
            formattingExecutor.execute(() -> deliver(event, config));
            return ReportAdmission.QUEUED;
        } catch (RejectedExecutionException exception) {
            recent.remove(key, now);
            FairPlayFairRule.LOGGER.warn(
                    "Resource-pack violation report queue is full; enforcement was unaffected.");
            return ReportAdmission.QUEUE_FULL;
        }
    }

    private void deliver(ResourcePackViolationEvent event, ReportingConfig config) {
        JsonObject summary = summary(event);
        if (!event.violation().manifestSafeToAttach()) {
            observe(delivery.deliverNotification(config.webhookUri, summary));
            return;
        }
        DiscordManifestDeliveryService.ManifestDeliveryRequest request =
                DiscordManifestDeliveryService.ManifestDeliveryRequest.generated(
                        config.webhookUri, event.authenticatedPlayerId(), event.timestamp(),
                        () -> ResourcePackViolationEvidenceBuilder.build(event), summary,
                        config.hastebinMirrorEnabled);
        observe(delivery.deliver(request));
    }

    private static void observe(java.util.concurrent.CompletableFuture<
            DiscordManifestDeliveryService.DeliveryResult> result) {
        result.whenComplete((deliveryResult, failure) -> {
            if (failure != null || deliveryResult == null || !deliveryResult.success()) {
                FairPlayFairRule.LOGGER.warn(
                        "Resource-pack violation Discord alert failed; enforcement was unaffected.");
            }
        });
    }

    private synchronized boolean duplicate(DedupKey key, long now) {
        long cutoff = now - DEDUP_WINDOW.toMillis();
        Long previous = recent.get(key);
        if (previous != null && previous >= cutoff) return true;
        if (recent.size() >= MAX_DEDUP_ENTRIES) {
            recent.entrySet().removeIf(entry -> entry.getValue() < cutoff);
            if (recent.size() >= MAX_DEDUP_ENTRIES) {
                recent.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue))
                        .ifPresent(entry -> recent.remove(entry.getKey(), entry.getValue()));
            }
        }
        recent.put(key, now);
        return false;
    }

    int dedupSize() { return recent.size(); }

    static JsonObject summary(ResourcePackViolationEvent event) {
        ResourcePackViolation violation = event.violation();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Resource Pack Integrity Violation");
        embed.addProperty("color", 15158332);
        embed.addProperty("description", boundedSingleLine(violation.reason(), 1_500));
        JsonArray fields = new JsonArray();
        fields.add(field("Player", event.playerName(), true));
        fields.add(field("UUID", event.authenticatedPlayerId().toString(), true));
        fields.add(field("Phase", event.phase().name(), true));
        fields.add(field("Violation", violation.code().name(), false));
        fields.add(field("Minecraft", event.minecraftVersion(), true));
        fields.add(field("Forge", event.forgeVersion(), true));
        fields.add(field("FPFR / Protocol", event.fpfrVersion() + " / "
                + event.protocolVersion(), false));
        addField(fields, "Detected Pack", violation.detectedPack());
        addField(fields, "Expected Hash", violation.expectedHash());
        addField(fields, "Received Hash", violation.receivedHash());
        fields.add(field("Action", event.phase().action(), false));
        fields.add(field("UTC Timestamp", event.timestamp().toString(), false));
        embed.add("fields", fields);
        JsonArray embeds = new JsonArray(); embeds.add(embed);
        JsonObject payload = new JsonObject(); payload.add("embeds", embeds);
        JsonObject mentions = new JsonObject(); mentions.add("parse", new JsonArray());
        payload.add("allowed_mentions", mentions);
        return payload;
    }

    private static void addField(JsonArray fields, String name, String value) {
        if (value != null && !value.isBlank()) fields.add(field(name, value, false));
    }
    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", boundedSingleLine(value, 1_000));
        field.addProperty("inline", inline);
        return field;
    }
    private static String boundedSingleLine(String value, int maximum) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("[\\p{Cntrl}]", " ");
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    @Override public void close() { formattingExecutor.shutdownNow(); }

    public enum ReportAdmission { QUEUED, DEDUPLICATED, DISABLED, QUEUE_FULL }
    public record ReportingConfig(URI webhookUri, boolean enabled, boolean hastebinMirrorEnabled) { }

    private record DedupKey(java.util.UUID playerId, ResourcePackViolationPhase phase,
                            String code, String receivedIdentity) {
        private static DedupKey from(ResourcePackViolationEvent event) {
            ResourcePackViolation violation = event.violation();
            String identity = violation.receivedHash().isBlank()
                    ? violation.detectedPack() + '|' + violation.comparisonDetail()
                    : violation.receivedHash();
            return new DedupKey(event.authenticatedPlayerId(), event.phase(),
                    violation.code().name(), identity);
        }
    }
}
