package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackPolicyConfigurationTest {
    private static final String A = "a".repeat(64);

    @Test
    void acceptsTrimmedCaseNormalizedExactHashButNeverStripsInvalidCharacters() {
        var valid = ResourcePackPolicyService.load(true, List.of("  " + A.toUpperCase() + "  "),
                List.of(), List.of(), List.of(), List.of());
        assertTrue(valid.errors().isEmpty());
        assertEquals(1, valid.policy().requiredCount());

        for (String malformed : List.of(A.substring(1), A + "0", "-" + A.substring(1),
                A.substring(0, 63) + "g", A.substring(0, 32) + " " + A.substring(33))) {
            assertFalse(ResourcePackPolicyService.load(true, List.of(malformed), List.of(),
                    List.of(), List.of(), List.of()).valid());
        }
    }

    @Test
    void rejectsExactDuplicatesCrossListDuplicatesAndInvalidUuid() {
        UUID player = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertFalse(ResourcePackPolicyService.load(true, List.of(A, A), List.of(),
                List.of(), List.of(), List.of()).valid());
        assertFalse(ResourcePackPolicyService.load(true, List.of(A), List.of(A),
                List.of(), List.of(), List.of()).valid());
        assertFalse(ResourcePackPolicyService.load(true, List.of(), List.of(),
                List.of("not-a-uuid=" + A), List.of(), List.of()).valid());
        assertFalse(ResourcePackPolicyService.load(true, List.of(), List.of(),
                List.of(player + "=" + A, player + "=" + A.toUpperCase()),
                List.of(), List.of()).valid());
    }

    @Test
    void rejectsListsBeyondConfiguredBounds() {
        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index <= ResourcePackLimits.MAX_POLICY_HASHES; index++) {
            tooMany.add(String.format("%064x", index));
        }
        assertFalse(ResourcePackPolicyService.load(true, tooMany, List.of(),
                List.of(), List.of(), List.of()).valid());

        List<String> tooManyPlayers = new ArrayList<>();
        for (int index = 0; index <= ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES; index++) {
            tooManyPlayers.add(new UUID(0, index) + "=" + String.format("%064x", index));
        }
        assertFalse(ResourcePackPolicyService.load(true, List.of(), List.of(),
                tooManyPlayers, List.of(), List.of()).valid());
    }
}
