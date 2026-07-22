package com.example.fairplayfairrule.resourcepack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable normalized policy with no Discord, networking, or session side effects. */
public final class ResourcePackPolicyService {
    private final boolean enabled;
    private final Set<String> required;
    private final Set<String> global;
    private final Map<UUID, Set<String>> perPlayer;
    private final Set<String> serverDownloaded;
    private final Set<String> banned;

    private ResourcePackPolicyService(boolean enabled, Set<String> required, Set<String> global,
                                      Map<UUID, Set<String>> perPlayer,
                                      Set<String> serverDownloaded, Set<String> banned) {
        this.enabled = enabled;
        this.required = Set.copyOf(required);
        this.global = Set.copyOf(global);
        Map<UUID, Set<String>> immutable = new HashMap<>();
        perPlayer.forEach((id, hashes) -> immutable.put(id, Set.copyOf(hashes)));
        this.perPlayer = Map.copyOf(immutable);
        this.serverDownloaded = Set.copyOf(serverDownloaded);
        this.banned = Set.copyOf(banned);
    }

    /** Compatibility overload for v2.0 callers and tests; integrity was always enabled there. */
    public static PolicyLoadResult load(Collection<String> required, Collection<String> global,
                                        Collection<String> playerEntries,
                                        Collection<String> serverDownloaded) {
        return load(true, required, global, playerEntries, serverDownloaded, List.of());
    }

    public static PolicyLoadResult load(boolean enabled, Collection<String> required,
                                        Collection<String> global, Collection<String> playerEntries,
                                        Collection<String> serverDownloaded,
                                        Collection<String> banned) {
        List<String> errors = new ArrayList<>();
        Set<String> approvalsSeen = new HashSet<>();
        Set<String> normalizedRequired = loadHashes("requiredPackHashes", required,
                ResourcePackLimits.MAX_POLICY_HASHES, errors, approvalsSeen);
        Set<String> normalizedGlobal = loadHashes("globalApprovedPackHashes", global,
                ResourcePackLimits.MAX_POLICY_HASHES, errors, approvalsSeen);
        Set<String> normalizedServer = loadHashes("serverDownloadedPackHashes", serverDownloaded,
                ResourcePackLimits.MAX_POLICY_HASHES, errors, approvalsSeen);
        Map<UUID, Set<String>> normalizedPlayers = loadPlayerEntries(playerEntries, errors);
        Set<String> normalizedBanned = loadHashes("bannedPackHashes", banned,
                ResourcePackLimits.MAX_POLICY_HASHES, errors, new HashSet<>());
        return new PolicyLoadResult(new ResourcePackPolicyService(enabled, normalizedRequired,
                normalizedGlobal, normalizedPlayers, normalizedServer, normalizedBanned), errors);
    }

    public boolean enabled() { return enabled; }
    public boolean requiresHashes() { return enabled || !banned.isEmpty(); }
    public int requiredCount() { return required.size(); }
    public int globalApprovedCount() { return global.size(); }
    public int perPlayerApprovedCount() {
        return perPlayer.values().stream().mapToInt(Set::size).sum();
    }
    public int serverDownloadedCount() { return serverDownloaded.size(); }
    public int bannedCount() { return banned.size(); }

    public ValidationResult validateJoin(UUID authenticatedPlayer,
                                         List<ResourcePackManifestEntry> manifest) {
        Inspection inspection = inspect(manifest, requiresHashes());
        if (inspection.failure != null) return inspection.failure;

        for (NormalizedEntry entry : inspection.enforcedEntries) {
            if (banned.contains(entry.hash)) {
                return rejected(inspection, violation(ValidationFailureCode.BANNED_PACK,
                        "Resource pack verification failed.\n\nExplicitly banned resource pack detected:\n"
                                + entry.name + "\n\nSHA-256:\n" + entry.hash
                                + "\n\nDisable or remove this pack and reconnect.",
                        entry.name, "", entry.hash, List.of(),
                        "Explicit ban takes precedence.", true));
            }
        }
        if (!enabled) return inspection.validResultWithoutBaseline();

        List<String> missing = new ArrayList<>();
        for (String hash : required) if (!inspection.hashes.contains(hash)) missing.add(hash);
        if (!missing.isEmpty()) {
            String expected = String.join("\n", missing);
            return rejected(inspection, violation(ValidationFailureCode.MISSING_REQUIRED_PACK,
                    "Resource pack verification failed.\n\nMissing required resource pack.\n\n"
                            + "Expected SHA-256:\n" + expected
                            + "\n\nRestore the approved pack and reconnect.",
                    "", expected, "", missing, "Required hashes absent.", true));
        }

        Set<String> allowed = new HashSet<>(required);
        allowed.addAll(global);
        allowed.addAll(perPlayer.getOrDefault(authenticatedPlayer, Set.of()));
        for (NormalizedEntry entry : inspection.enforcedEntries) {
            if (entry.type == ResourcePackType.SERVER_DOWNLOADED) {
                if (serverDownloaded.contains(entry.hash)) continue;
                return rejected(inspection, violation(ValidationFailureCode.UNAPPROVED_SERVER_PACK,
                        "Resource pack verification failed.\n\nUnexpected server-downloaded resource pack:\n"
                                + entry.name + "\n\nSHA-256:\n" + entry.hash
                                + "\n\nContact the server administrator and reconnect.",
                        entry.name, "", entry.hash, List.of(),
                        "Hash is absent from serverDownloadedPackHashes.", true));
            }
            if (allowed.contains(entry.hash)) continue;
            boolean anotherPlayer = perPlayer.entrySet().stream()
                    .anyMatch(value -> !value.getKey().equals(authenticatedPlayer)
                            && value.getValue().contains(entry.hash));
            ValidationFailureCode code = anotherPlayer
                    ? ValidationFailureCode.WRONG_PLAYER_APPROVAL
                    : ValidationFailureCode.UNAPPROVED_PACK;
            return rejected(inspection, violation(code,
                    "Resource pack verification failed.\n\nUnapproved resource pack detected:\n"
                            + entry.name + "\n\nSHA-256:\n" + entry.hash
                            + "\n\nDisable or remove this pack and reconnect.",
                    entry.name, "", entry.hash, List.of(),
                    anotherPlayer ? "Hash is approved only for another authenticated UUID."
                            : "Hash is absent from the effective allowlist.", true));
        }
        return inspection.validResult();
    }

    public ValidationResult validateManifestStructure(List<ResourcePackManifestEntry> manifest) {
        Inspection inspection = inspect(manifest, true);
        return inspection.failure == null ? inspection.validResult() : inspection.failure;
    }

    public ValidationResult validateRuntime(PlayerPackSessionStore.SessionBaseline baseline,
                                            List<ResourcePackManifestEntry> manifest) {
        Inspection inspection = inspect(manifest, true);
        if (inspection.failure != null) return inspection.failure;
        List<String> expected = baseline.orderedStateTokens();
        List<String> received = inspection.stateTokens();
        if (expected.equals(received)) return inspection.validResult();
        if (expected.size() == received.size()
                && new HashSet<>(expected).equals(new HashSet<>(received))) {
            return rejected(inspection, violation(ValidationFailureCode.SESSION_PACK_ORDER_CHANGED,
                    sessionChangedMessage(), "", "", "", List.of(),
                    "Active policy-controlled pack order changed.", true));
        }

        List<String> removed = new ArrayList<>(expected);
        received.forEach(removed::remove);
        List<String> added = new ArrayList<>(received);
        expected.forEach(added::remove);
        if (removed.size() == 1 && added.size() == 1) {
            String oldHash = removed.get(0);
            String newHash = added.get(0);
            String oldName = baseline.namesByStateToken().get(oldHash);
            String newName = inspection.stateNames().get(newHash);
            if (oldName != null && oldName.equals(newName)
                    && expected.indexOf(oldHash) == received.indexOf(newHash)) {
                return rejected(inspection, violation(ValidationFailureCode.SESSION_PACK_MODIFIED,
                        "Resource pack integrity check failed.\n\nModified or replaced resource pack:\n"
                                + newName + "\n\nExpected:\n" + oldHash + "\n\nReceived:\n"
                                + newHash + "\n\nDownload the exact approved ZIP and reconnect.",
                        newName, oldHash, newHash, List.of(),
                        "One same-position pack changed hash.", true));
            }
        }
        if (!added.isEmpty() && removed.isEmpty()) {
            String hash = added.get(0);
            return rejected(inspection, violation(ValidationFailureCode.SESSION_PACK_ADDED,
                    sessionChangedMessage() + "\n\nReceived SHA-256:\n" + hash,
                    inspection.stateNames().getOrDefault(hash, "Unknown resource pack"),
                    "", hash, List.of(), "Pack added during session.", true));
        }
        if (!removed.isEmpty() && added.isEmpty()) {
            String hash = removed.get(0);
            return rejected(inspection, violation(ValidationFailureCode.SESSION_PACK_REMOVED,
                    sessionChangedMessage() + "\n\nExpected SHA-256:\n" + hash,
                    baseline.namesByStateToken().getOrDefault(hash, "Unknown resource pack"),
                    hash, "", List.of(), "Pack removed during session.", true));
        }
        return rejected(inspection, violation(ValidationFailureCode.SESSION_STATE_CHANGED,
                sessionChangedMessage(), "", "", "", List.of(),
                "Multiple policy-controlled pack changes detected.", true));
    }

    private static Inspection inspect(List<ResourcePackManifestEntry> manifest, boolean hashesRequired) {
        if (manifest == null) return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                "The resource-pack manifest is missing.");
        if (manifest.size() > ResourcePackLimits.MAX_MANIFEST_ENTRIES) {
            return Inspection.unsafe(ValidationFailureCode.OVERSIZED_MANIFEST,
                    "The resource-pack manifest is oversized.");
        }
        List<String> hashes = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        List<NormalizedEntry> entries = new ArrayList<>();
        List<ResourcePackManifestEntry> normalized = new ArrayList<>();
        Set<String> trustedIdentities = new HashSet<>();
        ResourcePackViolation delayed = null;

        for (ResourcePackManifestEntry entry : manifest) {
            if (entry == null || entry.type() == null || entry.displayName() == null
                    || entry.sha256() == null || entry.identity() == null
                    || entry.displayName().isBlank()
                    || entry.displayName().length() > ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH
                    || entry.identity().length() > ResourcePackLimits.MAX_PACK_IDENTITY_LENGTH
                    || entry.size() < 0) {
                return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                        "The resource-pack manifest contains invalid fields.");
            }
            String name = safeName(entry.displayName());
            if (entry.type() == ResourcePackType.BUILT_IN) {
                if (entry.size() != 0 || !entry.sha256().isEmpty() || !entry.identity().isEmpty()) {
                    return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                            "A built-in resource-pack entry is malformed.");
                }
                normalized.add(new ResourcePackManifestEntry(name, "", 0, ResourcePackType.BUILT_IN));
                continue;
            }
            if (entry.type() == ResourcePackType.MOD_BUNDLED) {
                if (entry.size() != 0 || !entry.sha256().isEmpty()
                        || !ModBundledIdentity.isValid(entry.identity())) {
                    return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                            "A mod-bundled resource-pack entry is malformed.");
                }
                if (!trustedIdentities.add(entry.identity())) {
                    return Inspection.unsafe(ValidationFailureCode.DUPLICATE_MANIFEST_ENTRY,
                            "The resource-pack manifest contains a duplicate trusted identity.");
                }
                normalized.add(new ResourcePackManifestEntry(name, "", 0,
                        ResourcePackType.MOD_BUNDLED, entry.identity()));
                continue;
            }
            if (entry.type() == ResourcePackType.DIRECTORY) {
                if (!entry.sha256().isEmpty() || !entry.identity().isEmpty()) {
                    return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                            "A directory resource-pack entry is malformed.");
                }
                normalized.add(new ResourcePackManifestEntry(name, "", 0, ResourcePackType.DIRECTORY));
                if (hashesRequired && delayed == null) delayed = violation(
                        ValidationFailureCode.DIRECTORY_NOT_SUPPORTED,
                        "Resource pack verification failed.\n\nDirectory resource packs are not supported:\n"
                                + name + "\n\nCompress the pack as a ZIP, submit it for approval, and reconnect.",
                        name, "", "", List.of(), "Active directory pack.", true);
                continue;
            }
            if (entry.type() == ResourcePackType.UNRESOLVED) {
                if (!entry.sha256().isEmpty() || !entry.identity().isEmpty()) {
                    return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                            "An unresolved resource-pack entry is malformed.");
                }
                normalized.add(new ResourcePackManifestEntry(name, "", 0, ResourcePackType.UNRESOLVED));
                if (hashesRequired && delayed == null) delayed = violation(
                        ValidationFailureCode.UNRESOLVED_PACK,
                        "Resource pack verification failed.\n\nAn active resource pack could not be resolved or hashed:\n"
                                + name + "\n\nDisable the pack or restore its ZIP file and reconnect.",
                        name, "", "", List.of(), "Active pack resolution failed.", true);
                continue;
            }
            if (!entry.identity().isEmpty()) {
                return Inspection.unsafe(ValidationFailureCode.MALFORMED_MANIFEST,
                        "A file-backed resource-pack entry contains an unexpected identity.");
            }
            Optional<String> hash = HashNormalizer.normalize(entry.sha256());
            if (entry.size() <= 0 || (hashesRequired && hash.isEmpty())
                    || (!entry.sha256().isEmpty() && hash.isEmpty())) {
                return Inspection.unsafe(ValidationFailureCode.INVALID_HASH,
                        "An active resource pack has an invalid SHA-256 or file size.");
            }
            if (hash.isEmpty()) {
                normalized.add(new ResourcePackManifestEntry(name, "", entry.size(), entry.type()));
                continue;
            }
            if (names.containsKey(hash.get())) {
                return Inspection.unsafe(ValidationFailureCode.DUPLICATE_HASH,
                        "The resource-pack manifest contains a duplicate hash.");
            }
            hashes.add(hash.get());
            names.put(hash.get(), name);
            entries.add(new NormalizedEntry(name, hash.get(), entry.type()));
            normalized.add(new ResourcePackManifestEntry(name, hash.get(), entry.size(), entry.type()));
        }
        Inspection inspection = new Inspection(hashes, names, entries, normalized, null);
        return delayed == null ? inspection : inspection.withFailure(rejected(inspection, delayed));
    }

    private static ValidationResult rejected(Inspection inspection, ResourcePackViolation violation) {
        return ValidationResult.invalid(violation, inspection.hashes, inspection.names,
                inspection.normalized);
    }

    private static ResourcePackViolation violation(ValidationFailureCode code, String reason,
                                                   String name, String expected, String received,
                                                   List<String> missing, String detail, boolean attach) {
        return new ResourcePackViolation(code, reason, name, expected, received, missing, detail, attach);
    }

    private static String safeName(String name) { return name.replace('\r', ' ').replace('\n', ' '); }
    private static String sessionChangedMessage() {
        return "Resource pack state changed during this session.\n\nResource packs are locked after joining."
                + "\n\nRestore the approved configuration and reconnect.";
    }

    private static Set<String> loadHashes(String setting, Collection<String> values, int maximum,
                                          List<String> errors, Set<String> crossListSeen) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) { errors.add(setting + " is missing"); return result; }
        if (values.size() > maximum) errors.add(setting + " exceeds " + maximum + " entries");
        int count = 0;
        for (String raw : values) {
            if (count++ >= maximum) break;
            Optional<String> hash = HashNormalizer.normalize(raw);
            if (hash.isEmpty()) errors.add(setting + " contains an invalid SHA-256 value");
            else if (!result.add(hash.get())) errors.add(setting + " contains a duplicate SHA-256 value");
            else if (!crossListSeen.add(hash.get())) {
                result.remove(hash.get());
                errors.add(setting + " duplicates another approval list");
            }
        }
        return result;
    }

    private static Map<UUID, Set<String>> loadPlayerEntries(Collection<String> values,
                                                            List<String> errors) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        if (values == null) { errors.add("playerApprovedPackHashes is missing"); return result; }
        if (values.size() > ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES) {
            errors.add("playerApprovedPackHashes exceeds "
                    + ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES + " entries");
        }
        Set<String> exact = new HashSet<>();
        int count = 0;
        for (String raw : values) {
            if (count++ >= ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES) break;
            String value = raw == null ? "" : raw.trim();
            int separator = value.indexOf('=');
            if (separator <= 0 || separator != value.lastIndexOf('=')) {
                errors.add("playerApprovedPackHashes entry must use UUID=SHA256");
                continue;
            }
            UUID id;
            try { id = UUID.fromString(value.substring(0, separator).trim()); }
            catch (IllegalArgumentException exception) {
                errors.add("playerApprovedPackHashes contains an invalid UUID");
                continue;
            }
            Optional<String> hash = HashNormalizer.normalize(value.substring(separator + 1));
            if (hash.isEmpty()) {
                errors.add("playerApprovedPackHashes contains an invalid SHA-256 value");
                continue;
            }
            String pair = id + "=" + hash.get();
            if (!exact.add(pair)) {
                errors.add("playerApprovedPackHashes contains a duplicate entry");
                continue;
            }
            result.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(hash.get());
        }
        return result;
    }

    public record PolicyLoadResult(ResourcePackPolicyService policy, List<String> errors) {
        public PolicyLoadResult { errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty(); }
    }

    private record NormalizedEntry(String name, String hash, ResourcePackType type) { }

    private record Inspection(List<String> hashes, Map<String, String> names,
                              List<NormalizedEntry> enforcedEntries,
                              List<ResourcePackManifestEntry> normalized,
                              ValidationResult failure) {
        private List<String> stateTokens() { return hashes; }
        private Map<String, String> stateNames() { return names; }
        private ValidationResult validResult() {
            return ValidationResult.valid(hashes, names, normalized);
        }
        private ValidationResult validResultWithoutBaseline() {
            return ValidationResult.valid(List.of(), Map.of(), normalized);
        }
        private Inspection withFailure(ValidationResult value) {
            return new Inspection(hashes, names, enforcedEntries, normalized, value);
        }
        private static Inspection unsafe(ValidationFailureCode code, String diagnostic) {
            ResourcePackViolation violation = new ResourcePackViolation(code,
                    "Resource pack verification failed.\n\n" + diagnostic,
                    "", "", "", List.of(), diagnostic, false);
            return new Inspection(List.of(), Map.of(), List.of(), List.of(),
                    ValidationResult.invalid(violation));
        }
    }
}
