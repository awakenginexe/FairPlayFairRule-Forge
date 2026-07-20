package com.example.fairplayfairrule.server;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;

/** Bounded attachment plan or a safe summary-only rejection reason. */
public record ManifestAttachmentPlan(List<AttachmentPart> parts, String error) {
    public ManifestAttachmentPlan {
        parts = List.copyOf(parts);
        error = error == null ? "" : error;
    }

    public static ManifestAttachmentPlan deliverable(List<AttachmentPart> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Deliverable attachment plan requires at least one part");
        }
        return new ManifestAttachmentPlan(parts, "");
    }

    public static ManifestAttachmentPlan oversized(String error) {
        return new ManifestAttachmentPlan(List.of(), Objects.requireNonNull(error, "error"));
    }

    public boolean isDeliverable() {
        return error.isEmpty();
    }

    public static final class AttachmentPart {
        private final String fileName;
        private final byte[] backing;
        private final int offset;
        private final int length;

        public AttachmentPart(String fileName, byte[] content) {
            this(fileName, Objects.requireNonNull(content, "content").clone(), 0,
                    content.length);
        }

        private AttachmentPart(String fileName, byte[] backing, int offset, int length) {
            this.fileName = Objects.requireNonNull(fileName, "fileName");
            this.backing = Objects.requireNonNull(backing, "backing");
            if (offset < 0 || length < 0 || offset > backing.length - length) {
                throw new IllegalArgumentException("Invalid attachment byte slice");
            }
            this.offset = offset;
            this.length = length;
        }

        static AttachmentPart ownedSlice(
                String fileName, byte[] backing, int offset, int length) {
            return new AttachmentPart(fileName, backing, offset, length);
        }

        public String fileName() {
            return fileName;
        }

        public byte[] content() {
            return Arrays.copyOfRange(backing, offset, offset + length);
        }

        public int length() {
            return length;
        }

        byte[] backingForMultipart() {
            return backing;
        }

        int offsetForMultipart() {
            return offset;
        }
    }
}
