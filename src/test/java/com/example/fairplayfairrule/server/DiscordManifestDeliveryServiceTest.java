package com.example.fairplayfairrule.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordManifestDeliveryServiceTest {
    private static final URI DISCORD = URI.create("https://discord.test/api/webhooks/1/token?thread_id=7");
    private static final UUID PLAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant TIMESTAMP = Instant.parse("2026-07-20T12:34:56.789Z");

    @Test
    void hastebinTimeoutCannotBlockMultipartDiscordDelivery() {
        byte[] manifest = new byte[ManifestAttachmentPlanner.MAX_PART_BYTES + 1];
        Arrays.fill(manifest, (byte) 'x');
        manifest[ManifestAttachmentPlanner.MAX_PART_BYTES - 1] = '\n';
        CompletableFuture<BoundedHttpTransport.HttpResult> never = new CompletableFuture<>();
        RecordingTransport transport = new RecordingTransport(request -> {
            if (request.uri().getHost().equals("hst.sh")) {
                return never;
            }
            return completed(discordResponse(
                    "manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z-part-001-of-002.txt",
                    "manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z-part-002-of-002.txt"));
        });
        DiscordManifestDeliveryService service = service(transport, Duration.ofMillis(10));

        DiscordManifestDeliveryService.DeliveryResult result = service.deliver(request(manifest, true)).join();

        assertTrue(result.success());
        assertEquals(2, transport.requests.size());
        HttpRequest discord = transport.requests.stream()
                .filter(request -> request.uri().getHost().equals("discord.test"))
                .findFirst().orElseThrow();
        assertEquals("thread_id=7&wait=true", discord.uri().getQuery());
        assertTrue(discord.headers().firstValue("Content-Type").orElseThrow()
                .startsWith("multipart/form-data; boundary="));
        assertTrue(never.isCancelled());
        assertEquals(DiscordManifestDeliveryService.MAX_DISCORD_RESPONSE_BYTES,
                transport.limitForHost("discord.test"));
        assertEquals(DiscordManifestDeliveryService.MAX_HASTEBIN_RESPONSE_BYTES,
                transport.limitForHost("hst.sh"));
    }

    @Test
    void hastebinFailureCannotPreventDiscordAttachment() {
        RecordingTransport transport = new RecordingTransport(request -> {
            if (request.uri().getHost().equals("hst.sh")) {
                return CompletableFuture.failedFuture(new java.io.IOException("offline"));
            }
            return completed(discordResponse(
                    "manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z.txt"));
        });

        DiscordManifestDeliveryService.DeliveryResult result = service(transport, Duration.ofMillis(10))
                .deliver(request("mods\n".getBytes(StandardCharsets.UTF_8), true)).join();

        assertTrue(result.success());
        assertEquals(2, transport.requests.size());
    }

    @Test
    void disabledMirrorMakesNoHastebinRequest() {
        RecordingTransport transport = new RecordingTransport(request -> completed(discordResponse(
                "manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z.txt")));

        DiscordManifestDeliveryService.DeliveryResult result = service(transport, Duration.ofMillis(10))
                .deliver(request("mods\n".getBytes(StandardCharsets.UTF_8), false)).join();

        assertTrue(result.success());
        assertEquals(1, transport.requests.size());
        assertEquals("discord.test", transport.requests.get(0).uri().getHost());
    }

    @Test
    void discordStatusAndMalformedResponsesAreReported() {
        RecordingTransport rejected = new RecordingTransport(request -> completed(
                new BoundedHttpTransport.HttpResult(413, "too large".getBytes(StandardCharsets.UTF_8))));
        RecordingTransport malformed = new RecordingTransport(request -> completed(
                new BoundedHttpTransport.HttpResult(200, "{}".getBytes(StandardCharsets.UTF_8))));

        DiscordManifestDeliveryService.DeliveryResult statusResult = service(rejected, Duration.ZERO)
                .deliver(request("mods\n".getBytes(StandardCharsets.UTF_8), false)).join();
        DiscordManifestDeliveryService.DeliveryResult malformedResult = service(malformed, Duration.ZERO)
                .deliver(request("mods\n".getBytes(StandardCharsets.UTF_8), false)).join();

        assertFalse(statusResult.success());
        assertEquals("DISCORD_STATUS_413", statusResult.code());
        assertFalse(malformedResult.success());
        assertEquals("DISCORD_RESPONSE_INVALID", malformedResult.code());
    }

    @Test
    void overTotalLimitSendsMentionSafeSummaryWithoutHastebinOrAttachment() throws Exception {
        byte[] manifest = new byte[ManifestAttachmentPlanner.MAX_TOTAL_BYTES + 1];
        RecordingTransport transport = new RecordingTransport(request -> completed(
                new BoundedHttpTransport.HttpResult(200,
                        "{\"id\":\"message-1\",\"attachments\":[]}"
                                .getBytes(StandardCharsets.UTF_8))));

        DiscordManifestDeliveryService.DeliveryResult result = service(transport, Duration.ofMillis(10))
                .deliver(request(manifest, true)).join();

        assertTrue(result.success());
        assertEquals(1, transport.requests.size());
        HttpRequest discord = transport.requests.get(0);
        assertEquals("application/json; charset=UTF-8",
                discord.headers().firstValue("Content-Type").orElseThrow());
        String json = new String(readBody(discord), StandardCharsets.UTF_8);
        assertTrue(json.contains("Manifest Attachment Error"));
        assertTrue(json.contains("45 MiB"));
        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"));
    }

    @Test
    void rejectedQueueDoesNotConstructManifest() {
        AtomicBoolean constructed = new AtomicBoolean();
        DiscordManifestDeliveryService service = new DiscordManifestDeliveryService(
                new ManifestAttachmentPlanner(), (httpRequest, maximumResponseBytes) -> {
                    throw new AssertionError("HTTP must not run");
                }, command -> {
                    throw new RejectedExecutionException("full");
                }, Duration.ZERO, () -> "fpfrBoundary1234567890");
        DiscordManifestDeliveryService.ManifestDeliveryRequest request =
                DiscordManifestDeliveryService.ManifestDeliveryRequest.generated(
                        DISCORD, PLAYER_ID, TIMESTAMP, () -> {
                            constructed.set(true);
                            return "manifest\n".getBytes(StandardCharsets.UTF_8);
                        }, summary(), false);

        DiscordManifestDeliveryService.DeliveryResult result = service.deliver(request).join();

        assertFalse(result.success());
        assertEquals("DELIVERY_QUEUE_FULL", result.code());
        assertFalse(constructed.get());
    }

    @Test
    void waitParameterPreservesRawEncodedWebhookComponents() {
        URI encoded = URI.create(
                "https://discord.test/api/webhooks/1/to%2Fken?thread_name=hello%20world&wait=false");
        RecordingTransport transport = new RecordingTransport(request -> completed(discordResponse(
                "manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z.txt")));
        DiscordManifestDeliveryService.ManifestDeliveryRequest request =
                new DiscordManifestDeliveryService.ManifestDeliveryRequest(
                        encoded, PLAYER_ID, TIMESTAMP, "mods\n".getBytes(StandardCharsets.UTF_8),
                        summary(), false);

        assertTrue(service(transport, Duration.ZERO).deliver(request).join().success());
        URI sent = transport.requests.get(0).uri();
        assertEquals("/api/webhooks/1/to%2Fken", sent.getRawPath());
        assertEquals("thread_name=hello%20world&wait=true", sent.getRawQuery());
    }

    private static DiscordManifestDeliveryService service(
            BoundedHttpTransport transport, Duration mirrorWait) {
        return new DiscordManifestDeliveryService(
                new ManifestAttachmentPlanner(), transport, Runnable::run, mirrorWait,
                () -> "fpfrBoundary1234567890");
    }

    private static DiscordManifestDeliveryService.ManifestDeliveryRequest request(
            byte[] manifest, boolean mirrorEnabled) {
        return new DiscordManifestDeliveryService.ManifestDeliveryRequest(
                DISCORD, PLAYER_ID, TIMESTAMP, manifest, summary(), mirrorEnabled);
    }

    private static JsonObject summary() {
        JsonObject summary = new JsonObject();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Player Manifest");
        embed.add("fields", new JsonArray());
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        summary.add("embeds", embeds);
        return summary;
    }

    private static BoundedHttpTransport.HttpResult discordResponse(String... filenames) {
        StringBuilder json = new StringBuilder("{\"id\":\"message-1\",\"attachments\":[");
        for (int index = 0; index < filenames.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"filename\":\"").append(filenames[index]).append("\"}");
        }
        json.append("]}");
        return new BoundedHttpTransport.HttpResult(200, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static CompletableFuture<BoundedHttpTransport.HttpResult> completed(
            BoundedHttpTransport.HttpResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static byte[] readBody(HttpRequest request) throws Exception {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(output.toByteArray());
            }
        });
        return result.get();
    }

    private static final class RecordingTransport implements BoundedHttpTransport {
        private final java.util.function.Function<HttpRequest,
                CompletableFuture<BoundedHttpTransport.HttpResult>> responder;
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<Integer> responseLimits = new ArrayList<>();

        private RecordingTransport(java.util.function.Function<HttpRequest,
                CompletableFuture<BoundedHttpTransport.HttpResult>> responder) {
            this.responder = responder;
        }

        @Override
        public CompletableFuture<HttpResult> send(HttpRequest request, int maximumResponseBytes) {
            requests.add(request);
            responseLimits.add(maximumResponseBytes);
            return responder.apply(request);
        }

        private int limitForHost(String host) {
            for (int index = 0; index < requests.size(); index++) {
                if (requests.get(index).uri().getHost().equals(host)) {
                    return responseLimits.get(index);
                }
            }
            throw new AssertionError("No request for " + host);
        }
    }
}
