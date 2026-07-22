package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackPolicyServiceTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REQUIRED = "a".repeat(64);
    private static final String GLOBAL = "b".repeat(64);
    private static final String PLAYER_ONLY = "c".repeat(64);
    private static final String SECOND_PLAYER_ONLY = "d".repeat(64);
    private static final String UNKNOWN = "e".repeat(64);
    private static final String SERVER = "f".repeat(64);

    @Test
    void disabledIntegrityAllowsDiagnosticCustomEntriesAndCreatesNoLockingState() {
        ResourcePackPolicyService policy = ResourcePackPolicyService.load(false,
                List.of(), List.of(), List.of(), List.of(), List.of()).policy();
        ValidationResult result = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("plain.zip", "", 10, ResourcePackType.ZIP),
                new ResourcePackManifestEntry("development", "", 0, ResourcePackType.DIRECTORY),
                new ResourcePackManifestEntry("unknown", "", 0, ResourcePackType.UNRESOLVED)));
        assertTrue(result.isValid());
        assertTrue(result.orderedStateTokens().isEmpty());
    }

    @Test
    void enabledEmptyPolicyRejectsCustomButAllowsTrustedDiagnosticPacks() {
        ResourcePackPolicyService policy = ResourcePackPolicyService.load(true,
                List.of(), List.of(), List.of(), List.of(), List.of()).policy();
        assertEquals(ValidationFailureCode.UNAPPROVED_PACK,
                policy.validateJoin(PLAYER, List.of(zip("vanilla.zip", REQUIRED))).code());
        assertTrue(policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("vanilla", "", 0, ResourcePackType.BUILT_IN),
                new ResourcePackManifestEntry("Forge Mods", "", 0, ResourcePackType.MOD_BUNDLED,
                        ModBundledIdentity.fromTrustedProfileKey("forge:aggregate")))).isValid());
    }

    @Test
    void bannedHashOverridesEveryApproval() {
        ResourcePackPolicyService policy = ResourcePackPolicyService.load(true,
                List.of(REQUIRED), List.of(REQUIRED), List.of(PLAYER + "=" + REQUIRED),
                List.of(REQUIRED), List.of(REQUIRED)).policy();
        assertEquals(ValidationFailureCode.BANNED_PACK,
                policy.validateJoin(PLAYER, List.of(zip("banned.zip", REQUIRED))).code());
    }

    @Test
    void exactOrderedCustomAndServerBaselineDetectsReordering() {
        ResourcePackPolicyService policy = ResourcePackPolicyService.load(true,
                List.of(), List.of(REQUIRED, GLOBAL), List.of(), List.of(SERVER), List.of()).policy();
        ValidationResult joined = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("vanilla", "", 0, ResourcePackType.BUILT_IN),
                zip("a.zip", REQUIRED),
                new ResourcePackManifestEntry("server", SERVER, 10, ResourcePackType.SERVER_DOWNLOADED),
                zip("b.zip", GLOBAL)));
        assertTrue(joined.isValid());
        assertEquals(List.of(REQUIRED, SERVER, GLOBAL), joined.orderedStateTokens());
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(PLAYER, joined);
        assertEquals(ValidationFailureCode.SESSION_PACK_ORDER_CHANGED,
                policy.validateRuntime(store.get(PLAYER).orElseThrow(), List.of(
                        zip("b.zip", GLOBAL), zip("a.zip", REQUIRED),
                        new ResourcePackManifestEntry("server", SERVER, 10,
                                ResourcePackType.SERVER_DOWNLOADED))).code());
    }

    @Test
    void permitsOnlyOneApprovedServerDownloadedBootstrapCandidate() {
        ResourcePackPolicyService policy = ResourcePackPolicyService.load(true,
                List.of(), List.of(REQUIRED), List.of(), List.of(SERVER), List.of(UNKNOWN)).policy();
        ValidationResult joined = policy.validateJoin(PLAYER, List.of(zip("local.zip", REQUIRED)));
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(PLAYER, joined);
        var baseline = store.get(PLAYER).orElseThrow();

        assertTrue(policy.validateInitialServerPack(baseline, List.of(
                zip("local.zip", REQUIRED), new ResourcePackManifestEntry(
                        "server", SERVER, 10, ResourcePackType.SERVER_DOWNLOADED)))
                .orElseThrow().isValid());
        assertTrue(policy.validateInitialServerPack(baseline, List.of(
                zip("local.zip", REQUIRED), zip("not-server.zip", SERVER))).isEmpty());
        assertEquals(ValidationFailureCode.BANNED_PACK,
                policy.validateInitialServerPack(baseline, List.of(
                        zip("local.zip", REQUIRED), new ResourcePackManifestEntry(
                                "banned", UNKNOWN, 10, ResourcePackType.SERVER_DOWNLOADED)))
                        .orElseThrow().code());
    }

    @Test
    void acceptsRequiredSetWhenPresent() {
        ResourcePackPolicyService policy = policy(List.of(REQUIRED), List.of(), List.of(), List.of());

        ValidationResult result = policy.validateJoin(PLAYER, List.of(zip("Required.zip", REQUIRED)));

        assertTrue(result.isValid());
        assertEquals(java.util.Set.of(REQUIRED), result.activeHashes());
    }

    @Test
    void rejectsMissingRequiredHash() {
        ResourcePackPolicyService policy = policy(List.of(REQUIRED), List.of(), List.of(), List.of());

        ValidationResult result = policy.validateJoin(PLAYER, List.of());

        assertEquals(ValidationFailureCode.MISSING_REQUIRED_PACK, result.code());
        assertTrue(result.message().contains(REQUIRED));
    }

    @Test
    void rejectsUnknownExtraHash() {
        ValidationResult result = policy(List.of(), List.of(), List.of(), List.of())
                .validateJoin(PLAYER, List.of(zip("Unknown.zip", UNKNOWN)));

        assertEquals(ValidationFailureCode.UNAPPROVED_PACK, result.code());
        assertTrue(result.message().contains("Unknown.zip"));
    }

    @Test
    void acceptsGloballyApprovedOptionalHash() {
        ValidationResult result = policy(List.of(), List.of(GLOBAL.toUpperCase()), List.of(), List.of())
                .validateJoin(PLAYER, List.of(zip("Global.zip", GLOBAL)));

        assertTrue(result.isValid());
    }

    @Test
    void acceptsAllHashesAccumulatedForCorrectPlayer() {
        List<String> entries = List.of(
                PLAYER + "=" + PLAYER_ONLY,
                PLAYER + "=" + SECOND_PLAYER_ONLY
        );
        ResourcePackPolicyService policy = policy(List.of(), List.of(), entries, List.of());

        ValidationResult first = policy.validateJoin(PLAYER, List.of(zip("One.zip", PLAYER_ONLY)));
        ValidationResult second = policy.validateJoin(PLAYER, List.of(zip("Two.zip", SECOND_PLAYER_ONLY)));

        assertTrue(first.isValid());
        assertTrue(second.isValid());
    }

    @Test
    void rejectsPlayerHashForWrongUuid() {
        ResourcePackPolicyService policy = policy(
                List.of(), List.of(), List.of(PLAYER + "=" + PLAYER_ONLY), List.of());

        ValidationResult result = policy.validateJoin(OTHER_PLAYER, List.of(zip("Private.zip", PLAYER_ONLY)));

        assertEquals(ValidationFailureCode.WRONG_PLAYER_APPROVAL, result.code());
    }

    @Test
    void reportsAndExcludesMalformedAndDuplicatePlayerEntries() {
        List<String> entries = List.of(
                "not-a-uuid=" + PLAYER_ONLY,
                PLAYER + "=short",
                PLAYER + "=" + PLAYER_ONLY,
                PLAYER + "=" + PLAYER_ONLY.toUpperCase()
        );

        ResourcePackPolicyService.PolicyLoadResult loaded = ResourcePackPolicyService.load(
                List.of(), List.of(), entries, List.of());

        assertEquals(3, loaded.errors().size());
        assertTrue(loaded.errors().stream().anyMatch(error -> error.contains("UUID")));
        assertTrue(loaded.errors().stream().anyMatch(error -> error.contains("SHA-256")));
        assertTrue(loaded.errors().stream().anyMatch(error -> error.toLowerCase().contains("duplicate")));
        assertTrue(loaded.policy().validateJoin(PLAYER, List.of(zip("Private.zip", PLAYER_ONLY))).isValid());
    }

    @Test
    void rejectsDuplicateManifestHashes() {
        ResourcePackPolicyService policy = policy(List.of(), List.of(GLOBAL), List.of(), List.of());

        ValidationResult result = policy.validateJoin(PLAYER, List.of(
                zip("One.zip", GLOBAL),
                zip("Copy.zip", GLOBAL.toUpperCase())
        ));

        assertEquals(ValidationFailureCode.DUPLICATE_HASH, result.code());
    }

    @Test
    void rejectsEmptyAndInvalidHashes() {
        ResourcePackPolicyService policy = policy(List.of(), List.of(), List.of(), List.of());

        assertEquals(ValidationFailureCode.INVALID_HASH,
                policy.validateJoin(PLAYER, List.of(zip("Empty.zip", ""))).code());
        assertEquals(ValidationFailureCode.INVALID_HASH,
                policy.validateJoin(PLAYER, List.of(zip("Invalid.zip", "z".repeat(64)))).code());
    }

    @Test
    void rejectsDirectoryAndUnresolvedPacks() {
        ResourcePackPolicyService policy = policy(List.of(), List.of(), List.of(), List.of());

        ValidationResult directory = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("Dev Pack", "", 0, ResourcePackType.DIRECTORY)));
        ValidationResult unresolved = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("Mystery Pack", "", 0, ResourcePackType.UNRESOLVED)));

        assertEquals(ValidationFailureCode.DIRECTORY_NOT_SUPPORTED, directory.code());
        assertTrue(directory.message().contains("Dev Pack"));
        assertEquals(ValidationFailureCode.UNRESOLVED_PACK, unresolved.code());
        assertTrue(unresolved.message().contains("Mystery Pack"));
    }

    @Test
    void downloadedPackUsesSeparateServerExpectedSet() {
        ResourcePackManifestEntry downloaded = new ResourcePackManifestEntry(
                "Server Resources", SERVER, 42, ResourcePackType.SERVER_DOWNLOADED);

        ValidationResult globalOnly = policy(List.of(), List.of(SERVER), List.of(), List.of())
                .validateJoin(PLAYER, List.of(downloaded));
        ValidationResult configuredServer = policy(List.of(), List.of(), List.of(), List.of(SERVER))
                .validateJoin(PLAYER, List.of(downloaded));

        assertEquals(ValidationFailureCode.UNAPPROVED_SERVER_PACK, globalOnly.code());
        assertTrue(configuredServer.isValid());
    }

    @Test
    void builtInPackNeedsNoHashOrAllowlistEntry() {
        ResourcePackManifestEntry vanilla = new ResourcePackManifestEntry(
                "Vanilla", "", 0, ResourcePackType.BUILT_IN);

        ValidationResult result = policy(List.of(), List.of(), List.of(), List.of())
                .validateJoin(PLAYER, List.of(vanilla));

        assertTrue(result.isValid());
        assertTrue(result.activeHashes().isEmpty());
    }

    @Test
    void rejectsOversizedManifestAndAllowsRepeatedTrustedBuiltInDiagnostics() {
        ResourcePackPolicyService policy = policy(List.of(), List.of(), List.of(), List.of());
        ResourcePackManifestEntry vanilla = new ResourcePackManifestEntry(
                "Vanilla", "", 0, ResourcePackType.BUILT_IN);

        ValidationResult duplicate = policy.validateJoin(PLAYER, List.of(vanilla, vanilla));
        ValidationResult oversized = policy.validateJoin(PLAYER, java.util.Collections.nCopies(
                ResourcePackLimits.MAX_MANIFEST_ENTRIES + 1, vanilla));

        assertTrue(duplicate.isValid());
        assertEquals(ValidationFailureCode.OVERSIZED_MANIFEST, oversized.code());
        assertTrue(oversized.message().contains("oversized"));
    }

    @Test
    void structuralValidationDoesNotApplyAllowlistOrEstablishPolicyState() {
        ResourcePackPolicyService policy = policy(List.of(REQUIRED), List.of(), List.of(), List.of());

        ValidationResult structurallyValid = policy.validateManifestStructure(
                List.of(zip("Unapproved but well formed.zip", UNKNOWN)));
        ValidationResult structurallyInvalid = policy.validateManifestStructure(List.of(
                new ResourcePackManifestEntry("Broken", "", 0, ResourcePackType.UNRESOLVED)));

        assertTrue(structurallyValid.isValid());
        assertEquals(java.util.Set.of(UNKNOWN), structurallyValid.activeHashes());
        assertEquals(ValidationFailureCode.UNRESOLVED_PACK, structurallyInvalid.code());
    }

    private static ResourcePackPolicyService policy(List<String> required,
                                                    List<String> global,
                                                    List<String> players,
                                                    List<String> server) {
        ResourcePackPolicyService.PolicyLoadResult loaded = ResourcePackPolicyService.load(
                required, global, players, server);
        assertTrue(loaded.errors().isEmpty(), () -> "Unexpected policy errors: " + loaded.errors());
        return loaded.policy();
    }

    private static ResourcePackManifestEntry zip(String name, String hash) {
        return new ResourcePackManifestEntry(name, hash, 123, ResourcePackType.ZIP);
    }
}
