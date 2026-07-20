package com.example.fairplayfairrule.server;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Splits exact UTF-8 manifest bytes only at existing LF line boundaries. */
public final class ManifestAttachmentPlanner {
    public static final int MAX_PART_BYTES = 9 * 1024 * 1024;
    public static final int MAX_PARTS = 5;
    public static final int MAX_TOTAL_BYTES = 45 * 1024 * 1024;

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final int maximumPartBytes;
    private final int maximumParts;
    private final int maximumTotalBytes;

    public ManifestAttachmentPlanner() {
        this(MAX_PART_BYTES, MAX_PARTS, MAX_TOTAL_BYTES);
    }

    ManifestAttachmentPlanner(int maximumPartBytes, int maximumParts, int maximumTotalBytes) {
        if (maximumPartBytes <= 0 || maximumParts <= 0
                || maximumTotalBytes < maximumPartBytes) {
            throw new IllegalArgumentException("Attachment limits are invalid");
        }
        this.maximumPartBytes = maximumPartBytes;
        this.maximumParts = maximumParts;
        this.maximumTotalBytes = maximumTotalBytes;
    }

    public ManifestAttachmentPlan plan(UUID playerId, Instant timestamp, byte[] utf8Manifest) {
        Objects.requireNonNull(utf8Manifest, "utf8Manifest");
        if (utf8Manifest.length > maximumTotalBytes) {
            return totalLimitExceeded();
        }
        return planOwned(playerId, timestamp, utf8Manifest.clone());
    }

    /** Plans slices over a byte array whose ownership has been transferred to this operation. */
    ManifestAttachmentPlan planOwned(UUID playerId, Instant timestamp, byte[] utf8Manifest) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(utf8Manifest, "utf8Manifest");
        if (utf8Manifest.length > maximumTotalBytes) {
            return totalLimitExceeded();
        }

        List<Slice> slices = split(utf8Manifest);
        if (slices == null) {
            return ManifestAttachmentPlan.oversized(
                    "Manifest contains a line larger than the per-part attachment limit.");
        }
        if (slices.size() > maximumParts) {
            return ManifestAttachmentPlan.oversized(
                    "Manifest exceeds the maximum Discord attachment part count of "
                            + maximumParts + ".");
        }

        String baseName = "manifest-" + playerId + '-' + FILE_TIMESTAMP.format(timestamp);
        List<ManifestAttachmentPlan.AttachmentPart> parts = new ArrayList<>(slices.size());
        for (int index = 0; index < slices.size(); index++) {
            String fileName = slices.size() == 1
                    ? baseName + ".txt"
                    : baseName + "-part-" + number(index + 1)
                    + "-of-" + number(slices.size()) + ".txt";
            Slice slice = slices.get(index);
            parts.add(ManifestAttachmentPlan.AttachmentPart.ownedSlice(
                    fileName, utf8Manifest, slice.offset(), slice.length()));
        }
        return ManifestAttachmentPlan.deliverable(parts);
    }

    private List<Slice> split(byte[] manifest) {
        if (manifest.length <= maximumPartBytes) {
            return List.of(new Slice(0, manifest.length));
        }
        List<Slice> parts = new ArrayList<>();
        int start = 0;
        while (start < manifest.length) {
            int remaining = manifest.length - start;
            if (remaining <= maximumPartBytes) {
                parts.add(new Slice(start, remaining));
                break;
            }

            int lastAllowed = start + maximumPartBytes - 1;
            int splitAt = -1;
            for (int index = lastAllowed; index >= start; index--) {
                if (manifest[index] == '\n') {
                    splitAt = index + 1;
                    break;
                }
            }
            if (splitAt < 0) {
                return null;
            }
            parts.add(new Slice(start, splitAt - start));
            if (parts.size() > maximumParts) {
                return parts;
            }
            start = splitAt;
        }
        return parts;
    }

    private static ManifestAttachmentPlan totalLimitExceeded() {
        return ManifestAttachmentPlan.oversized(
                "Manifest exceeds the 45 MiB total Discord attachment limit.");
    }

    private static String number(int number) {
        return String.format(java.util.Locale.ROOT, "%03d", number);
    }

    private record Slice(int offset, int length) {
    }
}
