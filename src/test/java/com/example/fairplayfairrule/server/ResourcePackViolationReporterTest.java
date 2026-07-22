package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import com.example.fairplayfairrule.resourcepack.ResourcePackViolation;
import com.example.fairplayfairrule.resourcepack.ValidationFailureCode;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackViolationReporterTest {
    private static final UUID PLAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final URI WEBHOOK = URI.create("https://discord.test/api/webhooks/1/secret");

    @Test
    void summaryIsRedHighPriorityAuthenticatedAndMentionSafe() {
        JsonObject summary = ResourcePackViolationReporter.summary(event(A, true,
                ResourcePackViolationPhase.LOGIN, "Player\nName"));
        JsonObject embed = summary.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Resource Pack Integrity Violation", embed.get("title").getAsString());
        assertEquals(15158332, embed.get("color").getAsInt());
        assertTrue(embed.getAsJsonArray("fields").toString().contains(PLAYER.toString()));
        assertTrue(embed.getAsJsonArray("fields").toString().contains("LOGIN"));
        assertTrue(summary.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        assertFalse(embed.toString().contains("\nName"));
    }

    @Test
    void safeRejectedManifestAttachesEvidenceButMalformedIsSummaryOnly() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        try (ResourcePackViolationReporter reporter = reporter(transport, () -> 1_000L, true, 8)) {
            assertEquals(ResourcePackViolationReporter.ReportAdmission.QUEUED,
                    reporter.report(event(A, true, ResourcePackViolationPhase.LOGIN, "Player")));
            await(transport, 1);
            assertTrue(transport.requests.get(0).headers().firstValue("Content-Type")
                    .orElseThrow().startsWith("multipart/form-data"));

            reporter.report(event(B, false, ResourcePackViolationPhase.RUNTIME_RELOAD, "Player"));
            await(transport, 2);
            assertEquals("application/json; charset=UTF-8", transport.requests.get(1).headers()
                    .firstValue("Content-Type").orElseThrow());
        }
    }

    @Test
    void disabledReportingCreatesNoTaskAndDedupIsBounded() {
        RecordingTransport transport = new RecordingTransport();
        try (ResourcePackViolationReporter disabled = reporter(transport, () -> 1_000L, false, 8)) {
            assertEquals(ResourcePackViolationReporter.ReportAdmission.DISABLED,
                    disabled.report(event(A, true, ResourcePackViolationPhase.LOGIN, "Player")));
            assertTrue(transport.requests.isEmpty());
        }

        try (ResourcePackViolationReporter reporter = reporter(
                transport, () -> 1_000L, true, 2_048)) {
            for (int index = 0; index < ResourcePackViolationReporter.MAX_DEDUP_ENTRIES + 20; index++) {
                reporter.report(event(String.format("%064x", index), true,
                        ResourcePackViolationPhase.LOGIN, "Player"));
            }
            assertTrue(reporter.dedupSize() <= ResourcePackViolationReporter.MAX_DEDUP_ENTRIES);
        }
    }

    @Test
    void identicalEventDeduplicatesChangedHashAndExpiredEventDoNot() {
        RecordingTransport transport = new RecordingTransport();
        AtomicLong clock = new AtomicLong(1_000L);
        try (ResourcePackViolationReporter reporter = reporter(transport, clock::get, true, 8)) {
            ResourcePackViolationEvent first = event(A, true, ResourcePackViolationPhase.LOGIN, "Player");
            assertEquals(ResourcePackViolationReporter.ReportAdmission.QUEUED, reporter.report(first));
            assertEquals(ResourcePackViolationReporter.ReportAdmission.DEDUPLICATED, reporter.report(first));
            assertEquals(ResourcePackViolationReporter.ReportAdmission.QUEUED,
                    reporter.report(event(B, true, ResourcePackViolationPhase.LOGIN, "Player")));
            clock.addAndGet(ResourcePackViolationReporter.DEDUP_WINDOW.toMillis() + 1);
            assertEquals(ResourcePackViolationReporter.ReportAdmission.QUEUED, reporter.report(first));
        }
    }

    private static ResourcePackViolationReporter reporter(RecordingTransport transport,
            java.util.function.LongSupplier clock, boolean enabled, int queueCapacity) {
        DiscordManifestDeliveryService delivery = new DiscordManifestDeliveryService(
                new ManifestAttachmentPlanner(), transport, Runnable::run, Duration.ZERO,
                () -> "fpfrBoundary1234567890");
        return new ResourcePackViolationReporter(() ->
                new ResourcePackViolationReporter.ReportingConfig(WEBHOOK, enabled, false),
                delivery, clock, queueCapacity);
    }

    private static ResourcePackViolationEvent event(String received, boolean attach,
            ResourcePackViolationPhase phase, String playerName) {
        ResourcePackViolation violation = new ResourcePackViolation(
                attach ? ValidationFailureCode.UNAPPROVED_PACK : ValidationFailureCode.MALFORMED_MANIFEST,
                "sanitized reason", "Detected.zip", B, received, List.of(),
                "safe comparison", attach);
        return new ResourcePackViolationEvent(playerName, PLAYER, "1.21.1", "Forge 52.1.15",
                "2.1.0", 3, Instant.parse("2026-07-20T12:34:56.789Z"), phase,
                violation, List.of("examplemod@1.0"), attach ? List.of(
                new ResourcePackManifestEntry("Detected.zip", received, 123, ResourcePackType.ZIP))
                : List.of());
    }

    private static void await(RecordingTransport transport, int count) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (transport.requests.size() < count && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(count, transport.requests.size());
    }

    private static final class RecordingTransport implements BoundedHttpTransport {
        private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        @Override public CompletableFuture<HttpResult> send(HttpRequest request, int maximumResponseBytes) {
            requests.add(request);
            boolean multipart = request.headers().firstValue("Content-Type").orElse("")
                    .startsWith("multipart/form-data");
            String attachments = multipart
                    ? "[{\"filename\":\"manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z-part-001-of-001.txt\"}]"
                    : "[]";
            return CompletableFuture.completedFuture(new HttpResult(200,
                    ("{\"id\":\"message\",\"attachments\":" + attachments + "}")
                            .getBytes(StandardCharsets.UTF_8)));
        }
    }
}
