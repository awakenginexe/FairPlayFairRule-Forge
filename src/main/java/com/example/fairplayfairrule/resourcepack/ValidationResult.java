package com.example.fairplayfairrule.resourcepack;

import java.util.Map;
import java.util.Set;

/** Immutable result used by both join policy and locked-session validation. */
public final class ValidationResult {
    private final boolean valid;
    private final ValidationFailureCode code;
    private final String message;
    private final Set<String> activeHashes;
    private final Map<String, String> namesByHash;

    private ValidationResult(boolean valid, ValidationFailureCode code, String message,
                             Set<String> activeHashes, Map<String, String> namesByHash) {
        this.valid = valid;
        this.code = code;
        this.message = message;
        this.activeHashes = Set.copyOf(activeHashes);
        this.namesByHash = Map.copyOf(namesByHash);
    }

    public static ValidationResult valid(Set<String> activeHashes, Map<String, String> namesByHash) {
        return new ValidationResult(true, ValidationFailureCode.NONE, "", activeHashes, namesByHash);
    }

    public static ValidationResult invalid(ValidationFailureCode code, String message) {
        return new ValidationResult(false, code, message, Set.of(), Map.of());
    }

    public boolean isValid() {
        return valid;
    }

    public ValidationFailureCode code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Set<String> activeHashes() {
        return activeHashes;
    }

    public Map<String, String> namesByHash() {
        return namesByHash;
    }
}
