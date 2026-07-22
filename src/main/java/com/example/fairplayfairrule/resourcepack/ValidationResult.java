package com.example.fairplayfairrule.resourcepack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable policy result containing only bounded, path-free evidence. */
public final class ValidationResult {
    private final ResourcePackViolation violation;
    private final List<String> orderedStateTokens;
    private final Map<String, String> namesByStateToken;
    private final List<ResourcePackManifestEntry> normalizedManifest;

    private ValidationResult(ResourcePackViolation violation, List<String> orderedStateTokens,
                             Map<String, String> namesByStateToken,
                             List<ResourcePackManifestEntry> normalizedManifest) {
        this.violation = violation;
        this.orderedStateTokens = List.copyOf(orderedStateTokens);
        this.namesByStateToken = Map.copyOf(namesByStateToken);
        this.normalizedManifest = List.copyOf(normalizedManifest);
    }

    public static ValidationResult valid(List<String> state, Map<String, String> names,
                                         List<ResourcePackManifestEntry> manifest) {
        return new ValidationResult(null, state, names, manifest);
    }

    public static ValidationResult valid(Set<String> hashes, Map<String, String> names) {
        return valid(List.copyOf(hashes), names, List.of());
    }

    public static ValidationResult invalid(ResourcePackViolation violation) {
        return new ValidationResult(violation, List.of(), Map.of(), List.of());
    }

    public static ValidationResult invalid(ResourcePackViolation violation, List<String> state,
                                           Map<String, String> names,
                                           List<ResourcePackManifestEntry> manifest) {
        return new ValidationResult(violation, state, names, manifest);
    }

    public static ValidationResult invalid(ValidationFailureCode code, String message) {
        return invalid(new ResourcePackViolation(code, message, "", "", "", List.of(), "", false));
    }

    public boolean isValid() { return violation == null; }
    public ResourcePackViolation violation() { return violation; }
    public ValidationFailureCode code() { return isValid() ? ValidationFailureCode.NONE : violation.code(); }
    public String message() { return isValid() ? "" : violation.reason(); }
    public List<String> orderedStateTokens() { return orderedStateTokens; }
    public Map<String, String> namesByStateToken() { return namesByStateToken; }
    public List<ResourcePackManifestEntry> normalizedManifest() { return normalizedManifest; }
    public Set<String> activeHashes() { return Set.copyOf(new LinkedHashSet<>(orderedStateTokens)); }
    public Map<String, String> namesByHash() { return namesByStateToken; }
}
