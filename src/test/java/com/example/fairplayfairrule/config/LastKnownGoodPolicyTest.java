package com.example.fairplayfairrule.config;

import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LastKnownGoodPolicyTest {
    private static final String A = "a".repeat(64);

    @Test
    void validCandidateReplacesAndInvalidCandidateRetainsLastKnownGood() {
        LastKnownGoodPolicy holder = new LastKnownGoodPolicy(ResourcePackPolicyService.load(
                false, List.of(), List.of(), List.of(), List.of(), List.of()).policy());
        var accepted = ResourcePackPolicyService.load(true, List.of(A), List.of(),
                List.of(), List.of(), List.of());
        assertTrue(holder.accept(accepted));
        assertTrue(holder.current().enabled());
        assertEquals(1, holder.current().requiredCount());

        var malformed = ResourcePackPolicyService.load(true, List.of("not-a-hash"), List.of(),
                List.of(), List.of(), List.of());
        assertFalse(holder.accept(malformed));
        assertSame(accepted.policy(), holder.current());
    }
}
