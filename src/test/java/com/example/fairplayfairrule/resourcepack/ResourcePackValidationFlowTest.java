package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackValidationFlowTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String APPROVED = "a".repeat(64);
    private static final String OTHER_APPROVED = "b".repeat(64);
    private static final String UNKNOWN = "c".repeat(64);

    @Test
    void failedInitialReportDoesNotStoreBaseline() {
        PlayerPackSessionStore store = new PlayerPackSessionStore();

        ResourcePackValidationCoordinator.FlowResult result = ResourcePackValidationCoordinator.validateReport(
                policy(), store, PLAYER, List.of(zip("Unknown.zip", UNKNOWN)));

        assertFalse(result.validation().isValid());
        assertFalse(result.baselineEstablished());
        assertTrue(store.get(PLAYER).isEmpty());
    }

    @Test
    void successfulInitialReportStoresBaselineAfterPolicyPasses() {
        PlayerPackSessionStore store = new PlayerPackSessionStore();

        ResourcePackValidationCoordinator.FlowResult result = ResourcePackValidationCoordinator.validateReport(
                policy(), store, PLAYER, List.of(zip("Approved.zip", APPROVED)));

        assertTrue(result.validation().isValid());
        assertTrue(result.baselineEstablished());
        assertEquals(List.of(APPROVED), store.get(PLAYER).orElseThrow().hashes());
    }

    @Test
    void reloadBeforeBaselineIsFullJoinCandidateNotMissingBaselineFailure() {
        PlayerPackSessionStore store = new PlayerPackSessionStore();

        ResourcePackValidationCoordinator.FlowResult result = ResourcePackValidationCoordinator.validateReport(
                policy(), store, PLAYER, List.of(zip("Approved.zip", APPROVED)));

        assertTrue(result.validation().isValid());
        assertTrue(result.baselineEstablished());
    }

    @Test
    void reloadBeforeBaselineStillFailsClosedOnPolicyFailure() {
        PlayerPackSessionStore store = new PlayerPackSessionStore();

        ResourcePackValidationCoordinator.FlowResult result = ResourcePackValidationCoordinator.validateReport(
                policy(), store, PLAYER, List.of(zip("Unknown.zip", UNKNOWN)));

        assertEquals(ValidationFailureCode.UNAPPROVED_PACK, result.validation().code());
        assertTrue(store.get(PLAYER).isEmpty());
    }

    @Test
    void existingBaselineCanNeverBeResetByAnotherInitialStyleReport() {
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        ResourcePackValidationCoordinator.validateReport(
                policy(), store, PLAYER, List.of(zip("Approved.zip", APPROVED)));

        ResourcePackValidationCoordinator.FlowResult changed = ResourcePackValidationCoordinator.validateReport(
                policy(), store, PLAYER, List.of(zip("Approved.zip", OTHER_APPROVED)));

        assertEquals(ValidationFailureCode.SESSION_PACK_MODIFIED, changed.validation().code());
        assertFalse(changed.baselineEstablished());
        assertEquals(List.of(APPROVED), store.get(PLAYER).orElseThrow().hashes());
    }

    private static ResourcePackPolicyService policy() {
        return ResourcePackPolicyService.load(
                List.of(), List.of(APPROVED, OTHER_APPROVED), List.of(), List.of()).policy();
    }

    private static ResourcePackManifestEntry zip(String name, String hash) {
        return new ResourcePackManifestEntry(name, hash, 20, ResourcePackType.ZIP);
    }
}
