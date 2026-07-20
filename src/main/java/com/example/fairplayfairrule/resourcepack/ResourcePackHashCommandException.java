package com.example.fairplayfairrule.resourcepack;

/** Safe administrator-facing failure that never contains a local filesystem path. */
public final class ResourcePackHashCommandException extends RuntimeException {
    public ResourcePackHashCommandException(String message) {
        super(message);
    }
}
