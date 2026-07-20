package com.example.fairplayfairrule.resourcepack;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Normalizes and validates raw SHA-256 text used by policy and packets. */
public final class HashNormalizer {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private HashNormalizer() {
    }

    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SHA_256.matcher(normalized).matches()
                ? Optional.of(normalized)
                : Optional.empty();
    }
}
