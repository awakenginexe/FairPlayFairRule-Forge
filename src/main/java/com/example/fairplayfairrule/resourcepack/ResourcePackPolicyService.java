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

/** Loads normalized allowlists and validates join/runtime resource-pack manifests. */
public final class ResourcePackPolicyService {
    private final Set<String> requiredHashes;
    private final Set<String> globalApprovedHashes;
    private final Map<UUID, Set<String>> playerApprovedHashes;
    private final Set<String> serverDownloadedHashes;

    private ResourcePackPolicyService(Set<String> requiredHashes,
                                      Set<String> globalApprovedHashes,
                                      Map<UUID, Set<String>> playerApprovedHashes,
                                      Set<String> serverDownloadedHashes) {
        this.requiredHashes = Set.copyOf(requiredHashes);
        this.globalApprovedHashes = Set.copyOf(globalApprovedHashes);
        Map<UUID, Set<String>> immutablePlayers = new HashMap<>();
        playerApprovedHashes.forEach((uuid, hashes) -> immutablePlayers.put(uuid, Set.copyOf(hashes)));
        this.playerApprovedHashes = Map.copyOf(immutablePlayers);
        this.serverDownloadedHashes = Set.copyOf(serverDownloadedHashes);
    }

    public static PolicyLoadResult load(Collection<String> required,
                                        Collection<String> global,
                                        Collection<String> playerEntries,
                                        Collection<String> serverDownloaded) {
        List<String> errors = new ArrayList<>();
        Set<String> normalizedRequired = loadHashes("requiredPackHashes", required,
                ResourcePackLimits.MAX_POLICY_HASHES, errors);
        Set<String> normalizedGlobal = loadHashes("globalApprovedPackHashes", global,
                ResourcePackLimits.MAX_POLICY_HASHES, errors);
        Set<String> normalizedServer = loadHashes("serverDownloadedPackHashes", serverDownloaded,
                ResourcePackLimits.MAX_POLICY_HASHES, errors);
        Map<UUID, Set<String>> normalizedPlayers = loadPlayerEntries(playerEntries, errors);
        return new PolicyLoadResult(new ResourcePackPolicyService(
                normalizedRequired, normalizedGlobal, normalizedPlayers, normalizedServer), errors);
    }

    public ValidationResult validateJoin(UUID playerId, List<ResourcePackManifestEntry> manifest) {
        ManifestInspection inspection = inspectManifest(manifest);
        if (!inspection.result.isValid()) {
            return inspection.result;
        }

        Set<String> hashes = inspection.result.activeHashes();
        for (String required : requiredHashes) {
            if (!hashes.contains(required)) {
                return ValidationResult.invalid(ValidationFailureCode.MISSING_REQUIRED_PACK,
                        "Resource pack verification failed.\n\n" +
                        "Missing required resource pack.\n\n" +
                        "Expected SHA-256:\n" + required + "\n\n" +
                        "Restore the approved pack and reconnect.");
            }
        }

        Set<String> effectiveUserHashes = new HashSet<>(requiredHashes);
        effectiveUserHashes.addAll(globalApprovedHashes);
        effectiveUserHashes.addAll(playerApprovedHashes.getOrDefault(playerId, Set.of()));

        for (NormalizedEntry entry : inspection.entries) {
            if (entry.type == ResourcePackType.ZIP && !effectiveUserHashes.contains(entry.hash)) {
                return ValidationResult.invalid(ValidationFailureCode.UNAPPROVED_PACK,
                        "Resource pack verification failed.\n\n" +
                        "Unapproved resource pack detected:\n" + entry.name + "\n\n" +
                        "SHA-256:\n" + entry.hash + "\n\n" +
                        "Disable or remove this pack and reconnect.");
            }
            if (entry.type == ResourcePackType.SERVER_DOWNLOADED
                    && !serverDownloadedHashes.contains(entry.hash)) {
                return ValidationResult.invalid(ValidationFailureCode.UNAPPROVED_SERVER_PACK,
                        "Resource pack verification failed.\n\n" +
                        "Unexpected server-downloaded resource pack:\n" + entry.name + "\n\n" +
                        "SHA-256:\n" + entry.hash + "\n\n" +
                        "Contact the server administrator and reconnect.");
            }
        }
        return inspection.result;
    }

    /** Validates packet semantics without applying allowlists or mutating session state. */
    public ValidationResult validateManifestStructure(List<ResourcePackManifestEntry> manifest) {
        return inspectManifest(manifest).result;
    }

    public ValidationResult validateRuntime(PlayerPackSessionStore.SessionBaseline baseline,
                                            List<ResourcePackManifestEntry> manifest) {
        ManifestInspection inspection = inspectManifest(manifest);
        if (!inspection.result.isValid()) {
            return inspection.result;
        }
        Set<String> received = inspection.result.activeHashes();
        if (baseline.hashes().equals(received)) {
            return inspection.result;
        }

        Set<String> removed = new LinkedHashSet<>(baseline.hashes());
        removed.removeAll(received);
        Set<String> added = new LinkedHashSet<>(received);
        added.removeAll(baseline.hashes());

        if (removed.size() == 1 && added.size() == 1) {
            String expected = removed.iterator().next();
            String actual = added.iterator().next();
            String expectedName = baseline.namesByHash().get(expected);
            String actualName = inspection.result.namesByHash().get(actual);
            if (expectedName != null && expectedName.equals(actualName)) {
                return ValidationResult.invalid(ValidationFailureCode.SESSION_PACK_MODIFIED,
                        "Resource pack integrity check failed.\n\n" +
                        "Modified or replaced resource pack:\n" + actualName + "\n\n" +
                        "Expected:\n" + expected + "\n\n" +
                        "Received:\n" + actual + "\n\n" +
                        "Download the approved version and reconnect.");
            }
        }

        if (!added.isEmpty()) {
            String hash = added.iterator().next();
            String name = inspection.result.namesByHash().getOrDefault(hash, "Unknown resource pack");
            return ValidationResult.invalid(ValidationFailureCode.SESSION_PACK_ADDED,
                    "Resource pack integrity check failed.\n\n" +
                    "Resource pack added during this session:\n" + name + "\n\n" +
                    "SHA-256:\n" + hash + "\n\nReconnect to change active resource packs.");
        }

        String hash = removed.iterator().next();
        String name = baseline.namesByHash().getOrDefault(hash, "Unknown resource pack");
        return ValidationResult.invalid(ValidationFailureCode.SESSION_PACK_REMOVED,
                "Resource pack integrity check failed.\n\n" +
                "Resource pack removed during this session:\n" + name + "\n\n" +
                "Expected SHA-256:\n" + hash + "\n\nReconnect to change active resource packs.");
    }

    private static ManifestInspection inspectManifest(List<ResourcePackManifestEntry> manifest) {
        if (manifest == null || manifest.size() > ResourcePackLimits.MAX_MANIFEST_ENTRIES) {
            return ManifestInspection.failure(ValidationFailureCode.MALFORMED_MANIFEST,
                    "Resource pack verification failed.\n\nThe resource-pack manifest is missing or oversized.");
        }
        Set<String> hashes = new LinkedHashSet<>();
        Set<String> builtInEntries = new HashSet<>();
        Map<String, String> names = new LinkedHashMap<>();
        List<NormalizedEntry> normalizedEntries = new ArrayList<>();

        for (ResourcePackManifestEntry entry : manifest) {
            if (entry == null || entry.type() == null || entry.displayName() == null
                    || entry.displayName().isBlank()
                    || entry.displayName().length() > ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH
                    || entry.size() < 0) {
                return ManifestInspection.failure(ValidationFailureCode.MALFORMED_MANIFEST,
                        "Resource pack verification failed.\n\nThe resource-pack manifest is malformed.");
            }
            String safeName = entry.displayName();
            if (entry.type() == ResourcePackType.BUILT_IN) {
                if (entry.size() != 0 || entry.sha256() == null || !entry.sha256().isEmpty()) {
                    return ManifestInspection.failure(ValidationFailureCode.MALFORMED_MANIFEST,
                            "Resource pack verification failed.\n\nA built-in manifest entry is malformed.");
                }
                if (!builtInEntries.add(safeName)) {
                    return ManifestInspection.failure(ValidationFailureCode.MALFORMED_MANIFEST,
                            "Resource pack verification failed.\n\n" +
                            "Duplicate built-in manifest entry detected:\n" + safeName);
                }
                continue;
            }
            if (entry.type() == ResourcePackType.DIRECTORY) {
                return ManifestInspection.failure(ValidationFailureCode.DIRECTORY_NOT_SUPPORTED,
                        "Resource pack verification failed.\n\n" +
                        "Directory resource packs are not supported:\n" + safeName + "\n\n" +
                        "Compress the pack as a ZIP, submit it for approval, and reconnect.");
            }
            if (entry.type() == ResourcePackType.UNRESOLVED) {
                return ManifestInspection.failure(ValidationFailureCode.UNRESOLVED_PACK,
                        "Resource pack verification failed.\n\n" +
                        "An active resource pack could not be resolved or hashed:\n" + safeName + "\n\n" +
                        "Disable the pack or restore its ZIP file and reconnect.");
            }

            Optional<String> hash = HashNormalizer.normalize(entry.sha256());
            if (hash.isEmpty() || entry.size() == 0) {
                return ManifestInspection.failure(ValidationFailureCode.INVALID_HASH,
                        "Resource pack verification failed.\n\n" +
                        "Invalid SHA-256 for resource pack:\n" + safeName);
            }
            if (!hashes.add(hash.get())) {
                return ManifestInspection.failure(ValidationFailureCode.DUPLICATE_HASH,
                        "Resource pack verification failed.\n\n" +
                        "Duplicate resource-pack hash detected:\n" + hash.get());
            }
            names.put(hash.get(), safeName);
            normalizedEntries.add(new NormalizedEntry(safeName, hash.get(), entry.type()));
        }

        return new ManifestInspection(ValidationResult.valid(hashes, names), normalizedEntries);
    }

    private static Set<String> loadHashes(String setting, Collection<String> values,
                                          int maximum, List<String> errors) {
        Set<String> hashes = new LinkedHashSet<>();
        if (values == null) {
            errors.add(setting + " is missing; using an empty fail-closed set");
            return hashes;
        }
        if (values.size() > maximum) {
            errors.add(setting + " exceeds the maximum of " + maximum + " entries");
        }
        int count = 0;
        for (String value : values) {
            if (count++ >= maximum) {
                break;
            }
            Optional<String> hash = HashNormalizer.normalize(value);
            if (hash.isEmpty()) {
                errors.add(setting + " contains an invalid SHA-256 value");
            } else if (!hashes.add(hash.get())) {
                errors.add(setting + " contains a duplicate SHA-256 value: " + hash.get());
            }
        }
        return hashes;
    }

    private static Map<UUID, Set<String>> loadPlayerEntries(Collection<String> values,
                                                             List<String> errors) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        if (values == null) {
            errors.add("playerApprovedPackHashes is missing; using an empty set");
            return result;
        }
        if (values.size() > ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES) {
            errors.add("playerApprovedPackHashes exceeds the maximum of "
                    + ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES + " entries");
        }
        Set<String> exactPairs = new HashSet<>();
        int count = 0;
        for (String raw : values) {
            if (count++ >= ResourcePackLimits.MAX_PLAYER_POLICY_ENTRIES) {
                break;
            }
            String value = raw == null ? "" : raw.trim();
            int separator = value.indexOf('=');
            if (separator <= 0 || separator != value.lastIndexOf('=')) {
                errors.add("playerApprovedPackHashes entry must use UUID=SHA256");
                continue;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(value.substring(0, separator).trim());
            } catch (IllegalArgumentException exception) {
                errors.add("playerApprovedPackHashes contains an invalid UUID");
                continue;
            }
            Optional<String> hash = HashNormalizer.normalize(value.substring(separator + 1));
            if (hash.isEmpty()) {
                errors.add("playerApprovedPackHashes contains an invalid SHA-256 value for " + playerId);
                continue;
            }
            String pair = playerId + "=" + hash.get();
            if (!exactPairs.add(pair)) {
                errors.add("Duplicate playerApprovedPackHashes entry: " + pair);
                continue;
            }
            result.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>()).add(hash.get());
        }
        return result;
    }

    public record PolicyLoadResult(ResourcePackPolicyService policy, List<String> errors) {
        public PolicyLoadResult {
            errors = List.copyOf(errors);
        }
    }

    private record NormalizedEntry(String name, String hash, ResourcePackType type) {
    }

    private record ManifestInspection(ValidationResult result, List<NormalizedEntry> entries) {
        private static ManifestInspection failure(ValidationFailureCode code, String message) {
            return new ManifestInspection(ValidationResult.invalid(code, message), List.of());
        }
    }
}
