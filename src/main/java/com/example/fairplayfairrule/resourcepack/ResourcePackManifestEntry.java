package com.example.fairplayfairrule.resourcepack;

/** Safe wire representation of an active pack. Local paths never enter this type. */
public record ResourcePackManifestEntry(String displayName, String sha256, long size,
                                        ResourcePackType type, String identity) {
    public ResourcePackManifestEntry(String displayName, String sha256, long size,
                                     ResourcePackType type) {
        this(displayName, sha256, size, type, "");
    }
}
