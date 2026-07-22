package com.example.fairplayfairrule.resourcepack;

import java.util.List;

/** Immutable sanitized enforcement result; reporting consumes it only after policy decides. */
public record ResourcePackViolation(
        ValidationFailureCode code,
        String reason,
        String detectedPack,
        String expectedHash,
        String receivedHash,
        List<String> missingHashes,
        String comparisonDetail,
        boolean manifestSafeToAttach) {
    public ResourcePackViolation {
        missingHashes = missingHashes == null ? List.of() : List.copyOf(missingHashes);
        reason = safeReason(reason, 2_000);
        detectedPack = safe(detectedPack, ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH);
        expectedHash = safe(expectedHash, ResourcePackLimits.SHA256_LENGTH);
        receivedHash = safe(receivedHash, ResourcePackLimits.SHA256_LENGTH);
        comparisonDetail = safe(comparisonDetail, 1_000);
    }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String sanitized = value.replace('\r', ' ').replace('\n', ' ');
        return sanitized.length() <= maximum ? sanitized : sanitized.substring(0, maximum);
    }

    private static String safeReason(String value, int maximum) {
        if (value == null) return "";
        String sanitized = value.replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ");
        return sanitized.length() <= maximum ? sanitized : sanitized.substring(0, maximum);
    }
}
