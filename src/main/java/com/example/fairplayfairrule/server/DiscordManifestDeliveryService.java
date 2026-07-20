package com.example.fairplayfairrule.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Coordinates optional Hastebin mirroring and verified Discord attachment delivery. */
public final class DiscordManifestDeliveryService {
    public static final int MAX_DISCORD_RESPONSE_BYTES = 64 * 1024;
    public static final int MAX_HASTEBIN_RESPONSE_BYTES = 16 * 1024;

    private static final URI HASTEBIN_DOCUMENTS = URI.create("https://hst.sh/documents");
    private static final Pattern SAFE_HASTEBIN_KEY = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Duration DISCORD_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration HASTEBIN_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ManifestAttachmentPlanner planner;
    private final BoundedHttpTransport transport;
    private final Executor executor;
    private final Duration mirrorWait;
    private final Supplier<String> boundarySupplier;

    public DiscordManifestDeliveryService(
            ManifestAttachmentPlanner planner,
            BoundedHttpTransport transport,
            Executor executor,
            Duration mirrorWait,
            Supplier<String> boundarySupplier) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.mirrorWait = Objects.requireNonNull(mirrorWait, "mirrorWait");
        this.boundarySupplier = Objects.requireNonNull(boundarySupplier, "boundarySupplier");
        if (mirrorWait.isNegative() || mirrorWait.compareTo(Duration.ofSeconds(3)) > 0) {
            throw new IllegalArgumentException("Hastebin wait must be between zero and three seconds");
        }
    }

    public CompletableFuture<DeliveryResult> deliver(ManifestDeliveryRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return CompletableFuture.supplyAsync(() -> deliverOffThread(request), executor);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(DeliveryResult.failure("DELIVERY_QUEUE_FULL"));
        }
    }

    /** Sends a summary-only webhook while retaining the same thread and response guarantees. */
    public CompletableFuture<DeliveryResult> deliverNotification(URI webhookUri, JsonObject summary) {
        Objects.requireNonNull(webhookUri, "webhookUri");
        Objects.requireNonNull(summary, "summary");
        try {
            return CompletableFuture.supplyAsync(
                    () -> sendDiscordJson(webhookUri, summary), executor);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(DeliveryResult.failure("DELIVERY_QUEUE_FULL"));
        }
    }

    private DeliveryResult deliverOffThread(ManifestDeliveryRequest request) {
        byte[] manifest;
        try {
            manifest = request.materializeManifest();
        } catch (RuntimeException exception) {
            return DeliveryResult.failure("MANIFEST_BUILD_FAILED");
        }
        ManifestAttachmentPlan plan = manifest == null
                ? ManifestAttachmentPlan.oversized(
                        "Manifest exceeds the 45 MiB total Discord attachment limit.")
                : planner.planOwned(request.playerId(), request.timestamp(), manifest);
        if (!plan.isDeliverable()) {
            JsonObject summary = mentionSafe(request.summary());
            addDeliveryEmbed(summary, "Manifest Attachment Error", plan.error());
            return sendDiscordJson(request.webhookUri(), summary);
        }

        CompletableFuture<BoundedHttpTransport.HttpResult> hastebin = null;
        if (request.hastebinMirrorEnabled()) {
            hastebin = startHastebinMirror(manifest);
        }
        String mirrorUrl = awaitBestEffortMirror(hastebin);

        JsonObject summary = request.summary().deepCopy();
        String delivery = plan.parts().size() == 1
                ? "Complete UTF-8 manifest attached directly."
                : "Complete UTF-8 manifest attached in " + plan.parts().size()
                + " ordered parts.";
        if (mirrorUrl != null) {
            delivery += " Optional mirror: " + mirrorUrl;
        }
        addDeliveryEmbed(summary, "Manifest Delivery", delivery);

        DiscordMultipartPayload multipart;
        try {
            multipart = DiscordMultipartPayload.create(summary, plan.parts(), boundarySupplier.get());
        } catch (RuntimeException exception) {
            return DeliveryResult.failure("MULTIPART_INVALID");
        }

        HttpRequest discordRequest = HttpRequest.newBuilder(withWait(request.webhookUri()))
                .timeout(DISCORD_REQUEST_TIMEOUT)
                .header("Content-Type", multipart.contentType())
                .POST(multipart.bodyPublisher())
                .build();
        return verifyDiscord(
                await(transport.send(discordRequest, MAX_DISCORD_RESPONSE_BYTES)),
                plan.parts());
    }

    private CompletableFuture<BoundedHttpTransport.HttpResult> startHastebinMirror(byte[] manifest) {
        HttpRequest request = HttpRequest.newBuilder(HASTEBIN_DOCUMENTS)
                .timeout(HASTEBIN_REQUEST_TIMEOUT)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(manifest))
                .build();
        try {
            return transport.send(request, MAX_HASTEBIN_RESPONSE_BYTES);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private String awaitBestEffortMirror(CompletableFuture<BoundedHttpTransport.HttpResult> future) {
        if (future == null) {
            return null;
        }
        try {
            BoundedHttpTransport.HttpResult response = future.get(
                    mirrorWait.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonObject json = JsonParser.parseString(
                    new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            String key = json.has("key") ? json.get("key").getAsString() : null;
            return key != null && SAFE_HASTEBIN_KEY.matcher(key).matches()
                    ? "https://hst.sh/" + key : null;
        } catch (Exception exception) {
            future.cancel(true);
            return null;
        }
    }

    private DeliveryResult sendDiscordJson(URI webhookUri, JsonObject summary) {
        byte[] body = mentionSafe(summary).toString().getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(withWait(webhookUri))
                .timeout(DISCORD_REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return verifyDiscord(await(transport.send(request, MAX_DISCORD_RESPONSE_BYTES)), List.of());
    }

    private static BoundedHttpTransport.HttpResult await(
            CompletableFuture<BoundedHttpTransport.HttpResult> future) {
        try {
            return future.join();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static DeliveryResult verifyDiscord(
            BoundedHttpTransport.HttpResult response,
            List<ManifestAttachmentPlan.AttachmentPart> expectedParts) {
        if (response == null) {
            return DeliveryResult.failure("DISCORD_REQUEST_FAILED");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return DeliveryResult.failure("DISCORD_STATUS_" + response.statusCode());
        }
        try {
            JsonObject json = JsonParser.parseString(
                    new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("id") || json.get("id").getAsString().isBlank()) {
                return DeliveryResult.failure("DISCORD_RESPONSE_INVALID");
            }
            if (!expectedParts.isEmpty()) {
                JsonArray attachments = json.getAsJsonArray("attachments");
                if (attachments == null || attachments.size() != expectedParts.size()) {
                    return DeliveryResult.failure("DISCORD_ATTACHMENTS_MISMATCH");
                }
                for (int index = 0; index < expectedParts.size(); index++) {
                    JsonElement element = attachments.get(index);
                    if (!element.isJsonObject()
                            || !element.getAsJsonObject().has("filename")
                            || !expectedParts.get(index).fileName().equals(
                            element.getAsJsonObject().get("filename").getAsString())) {
                        return DeliveryResult.failure("DISCORD_ATTACHMENTS_MISMATCH");
                    }
                }
            }
            return DeliveryResult.ok();
        } catch (RuntimeException exception) {
            return DeliveryResult.failure("DISCORD_RESPONSE_INVALID");
        }
    }

    private static JsonObject mentionSafe(JsonObject source) {
        JsonObject json = source.deepCopy();
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        json.add("allowed_mentions", allowedMentions);
        return json;
    }

    private static void addDeliveryEmbed(JsonObject summary, String title, String description) {
        JsonArray embeds = summary.has("embeds") && summary.get("embeds").isJsonArray()
                ? summary.getAsJsonArray("embeds") : new JsonArray();
        if (!summary.has("embeds") || !summary.get("embeds").isJsonArray()) {
            summary.add("embeds", embeds);
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embeds.add(embed);
    }

    private static URI withWait(URI webhookUri) {
        String query = webhookUri.getRawQuery();
        StringBuilder updated = new StringBuilder();
        if (query != null && !query.isBlank()) {
            for (String parameter : query.split("&")) {
                if (!parameter.equals("wait") && !parameter.startsWith("wait=")) {
                    if (updated.length() > 0) {
                        updated.append('&');
                    }
                    updated.append(parameter);
                }
            }
        }
        if (updated.length() > 0) {
            updated.append('&');
        }
        updated.append("wait=true");
        String raw = webhookUri.toASCIIString();
        int fragmentAt = raw.indexOf('#');
        String fragment = fragmentAt >= 0 ? raw.substring(fragmentAt) : "";
        String withoutFragment = fragmentAt >= 0 ? raw.substring(0, fragmentAt) : raw;
        int queryAt = withoutFragment.indexOf('?');
        String base = queryAt >= 0 ? withoutFragment.substring(0, queryAt) : withoutFragment;
        return URI.create(base + '?' + updated.toString() + fragment);
    }

    public static final class ManifestDeliveryRequest {
        private final URI webhookUri;
        private final UUID playerId;
        private final Instant timestamp;
        private final Supplier<byte[]> manifestSupplier;
        private final boolean inputAlreadyOversized;
        private final JsonObject summary;
        private final boolean hastebinMirrorEnabled;

        public ManifestDeliveryRequest(
                URI webhookUri,
                UUID playerId,
                Instant timestamp,
                byte[] manifestUtf8,
                JsonObject summary,
                boolean hastebinMirrorEnabled) {
            this.webhookUri = Objects.requireNonNull(webhookUri, "webhookUri");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
            byte[] suppliedManifest = Objects.requireNonNull(manifestUtf8, "manifestUtf8");
            this.inputAlreadyOversized = suppliedManifest.length
                    > ManifestAttachmentPlanner.MAX_TOTAL_BYTES;
            byte[] ownedManifest = inputAlreadyOversized ? new byte[0] : suppliedManifest.clone();
            this.manifestSupplier = () -> ownedManifest;
            this.summary = Objects.requireNonNull(summary, "summary").deepCopy();
            this.hastebinMirrorEnabled = hastebinMirrorEnabled;
        }

        private ManifestDeliveryRequest(
                URI webhookUri,
                UUID playerId,
                Instant timestamp,
                Supplier<byte[]> manifestSupplier,
                JsonObject summary,
                boolean hastebinMirrorEnabled) {
            this.webhookUri = Objects.requireNonNull(webhookUri, "webhookUri");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
            this.manifestSupplier = Objects.requireNonNull(manifestSupplier, "manifestSupplier");
            this.inputAlreadyOversized = false;
            this.summary = Objects.requireNonNull(summary, "summary").deepCopy();
            this.hastebinMirrorEnabled = hastebinMirrorEnabled;
        }

        public static ManifestDeliveryRequest generated(
                URI webhookUri,
                UUID playerId,
                Instant timestamp,
                Supplier<byte[]> manifestSupplier,
                JsonObject summary,
                boolean hastebinMirrorEnabled) {
            return new ManifestDeliveryRequest(webhookUri, playerId, timestamp,
                    manifestSupplier, summary, hastebinMirrorEnabled);
        }

        public URI webhookUri() {
            return webhookUri;
        }

        public UUID playerId() {
            return playerId;
        }

        public Instant timestamp() {
            return timestamp;
        }

        byte[] materializeManifest() {
            if (inputAlreadyOversized) {
                return null;
            }
            byte[] manifest = Objects.requireNonNull(
                    manifestSupplier.get(), "generated manifest");
            return manifest.length > ManifestAttachmentPlanner.MAX_TOTAL_BYTES ? null : manifest;
        }

        public JsonObject summary() {
            return summary.deepCopy();
        }

        public boolean hastebinMirrorEnabled() {
            return hastebinMirrorEnabled;
        }
    }

    public record DeliveryResult(boolean success, String code) {
        static DeliveryResult ok() {
            return new DeliveryResult(true, "OK");
        }

        static DeliveryResult failure(String code) {
            return new DeliveryResult(false, code);
        }
    }
}
