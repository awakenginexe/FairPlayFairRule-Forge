package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackPolicyLogSummaryTest {
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String C = "c".repeat(64);
    private static final String D = "d".repeat(64);
    private static final String E = "e".repeat(64);
    private static final UUID PLAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void disabledEmptyPolicyReportsExplicitEffectiveState() {
        var loaded = ResourcePackPolicyService.load(false, List.of(), List.of(), List.of(), List.of(), List.of());
        var summary = ResourcePackPolicyLogSummary.from(loaded.policy(), loaded.errors().size());
        assertEquals("Resource-pack integrity policy loaded: enabled=false, required=0, global=0, "
                + "per-player=0, server-downloaded=0, banned=0, errors=0", summary.message());
        assertFalse(summary.inactiveApprovalWarningRequired());
    }

    @Test
    void enabledPolicyReportsCountsAndDisabledApprovalsWarn() {
        var enabled = ResourcePackPolicyService.load(true, List.of(A), List.of(B),
                List.of(PLAYER + "=" + C), List.of(D), List.of(E));
        assertEquals("Resource-pack integrity policy loaded: enabled=true, required=1, global=1, "
                        + "per-player=1, server-downloaded=1, banned=1, errors=0",
                ResourcePackPolicyLogSummary.from(enabled.policy(), 0).message());

        var disabled = ResourcePackPolicyService.load(false, List.of(A), List.of(),
                List.of(), List.of(), List.of());
        assertTrue(ResourcePackPolicyLogSummary.from(disabled.policy(), 0)
                .inactiveApprovalWarningRequired());
    }
}
