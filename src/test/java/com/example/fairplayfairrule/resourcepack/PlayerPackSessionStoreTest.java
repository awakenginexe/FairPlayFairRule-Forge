package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPackSessionStoreTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String FIRST = "1".repeat(64);
    private static final String SECOND = "2".repeat(64);
    private static final String THIRD = "3".repeat(64);

    @Test
    void storesOnlyValidatedImmutableBaselineAndClearsIt() {
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        ResourcePackPolicyService policy = policy(FIRST);
        ValidationResult valid = policy.validateJoin(PLAYER, List.of(zip("First.zip", FIRST)));
        ValidationResult invalid = policy.validateJoin(PLAYER, List.of(zip("Unknown.zip", THIRD)));

        assertThrows(IllegalArgumentException.class, () -> store.storeValidated(PLAYER, invalid));
        assertTrue(store.get(PLAYER).isEmpty());

        store.storeValidated(PLAYER, valid);
        PlayerPackSessionStore.SessionBaseline baseline = store.get(PLAYER).orElseThrow();
        assertEquals(Set.of(FIRST), baseline.hashes());
        assertThrows(UnsupportedOperationException.class, () -> baseline.hashes().add(SECOND));

        store.clear(PLAYER);
        assertTrue(store.get(PLAYER).isEmpty());
    }

    @Test
    void acceptsUnchangedExactHashSetRegardlessOfOrder() {
        ResourcePackPolicyService policy = policy(FIRST, SECOND);
        ValidationResult join = policy.validateJoin(PLAYER, List.of(
                zip("First.zip", FIRST), zip("Second.zip", SECOND)));
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(PLAYER, join);

        ValidationResult runtime = policy.validateRuntime(store.get(PLAYER).orElseThrow(), List.of(
                zip("Second renamed.zip", SECOND), zip("First renamed.zip", FIRST)));

        assertTrue(runtime.isValid());
    }

    @Test
    void rejectsPackAddedAfterReloadEvenWhenApproved() {
        ResourcePackPolicyService policy = policy(FIRST, SECOND);
        PlayerPackSessionStore.SessionBaseline baseline = baseline(policy, zip("First.zip", FIRST));

        ValidationResult result = policy.validateRuntime(baseline, List.of(
                zip("First.zip", FIRST), zip("Approved but new.zip", SECOND)));

        assertEquals(ValidationFailureCode.SESSION_PACK_ADDED, result.code());
        assertTrue(result.message().contains(SECOND));
    }

    @Test
    void rejectsPackRemovedAfterReload() {
        ResourcePackPolicyService policy = policy(FIRST, SECOND);
        ValidationResult join = policy.validateJoin(PLAYER, List.of(
                zip("First.zip", FIRST), zip("Second.zip", SECOND)));
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(PLAYER, join);

        ValidationResult result = policy.validateRuntime(store.get(PLAYER).orElseThrow(),
                List.of(zip("First.zip", FIRST)));

        assertEquals(ValidationFailureCode.SESSION_PACK_REMOVED, result.code());
        assertTrue(result.message().contains(SECOND));
    }

    @Test
    void classifiesSameNameChangedHashAsModifiedOrReplaced() {
        ResourcePackPolicyService policy = policy(FIRST, SECOND);
        PlayerPackSessionStore.SessionBaseline baseline = baseline(policy, zip("Faithful.zip", FIRST));

        ValidationResult result = policy.validateRuntime(baseline, List.of(zip("Faithful.zip", SECOND)));

        assertEquals(ValidationFailureCode.SESSION_PACK_MODIFIED, result.code());
        assertTrue(result.message().contains("Faithful.zip"));
        assertTrue(result.message().contains(FIRST));
        assertTrue(result.message().contains(SECOND));
    }

    @Test
    void rejectsUnresolvedAndDirectoryBeforeComparingBaseline() {
        ResourcePackPolicyService policy = policy(FIRST);
        PlayerPackSessionStore.SessionBaseline baseline = baseline(policy, zip("First.zip", FIRST));

        assertEquals(ValidationFailureCode.UNRESOLVED_PACK, policy.validateRuntime(baseline, List.of(
                new ResourcePackManifestEntry("Missing", "", 0, ResourcePackType.UNRESOLVED))).code());
        assertEquals(ValidationFailureCode.DIRECTORY_NOT_SUPPORTED, policy.validateRuntime(baseline, List.of(
                new ResourcePackManifestEntry("Dev", "", 0, ResourcePackType.DIRECTORY))).code());
    }

    private static PlayerPackSessionStore.SessionBaseline baseline(ResourcePackPolicyService policy,
                                                                    ResourcePackManifestEntry... entries) {
        ValidationResult join = policy.validateJoin(PLAYER, List.of(entries));
        assertTrue(join.isValid());
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(PLAYER, join);
        return store.get(PLAYER).orElseThrow();
    }

    private static ResourcePackPolicyService policy(String... approved) {
        ResourcePackPolicyService.PolicyLoadResult loaded = ResourcePackPolicyService.load(
                List.of(), List.of(approved), List.of(), List.of());
        assertTrue(loaded.errors().isEmpty());
        return loaded.policy();
    }

    private static ResourcePackManifestEntry zip(String name, String hash) {
        return new ResourcePackManifestEntry(name, hash, 10, ResourcePackType.ZIP);
    }
}
