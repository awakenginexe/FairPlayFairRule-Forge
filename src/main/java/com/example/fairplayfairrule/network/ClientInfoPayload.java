package com.example.fairplayfairrule.network;

import com.example.fairplayfairrule.resourcepack.ResourcePackLimits;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;

import java.util.List;

/** Shared, bounded packet data independent of Forge channel API changes. */
public record ClientInfoPayload(ResourcePackReportType reportType, List<String> modList,
                                List<ResourcePackManifestEntry> resourcePacks) {
    public static final int MAX_MOD_ENTRIES = 2_048;
    public static final int MAX_MOD_ENTRY_LENGTH = 256;

    public ClientInfoPayload {
        if (reportType == null || modList == null || resourcePacks == null) {
            throw new IllegalArgumentException("Client info payload fields are required");
        }
        if (modList.size() > MAX_MOD_ENTRIES) {
            throw new IllegalArgumentException("Too many mod entries: " + modList.size());
        }
        if (resourcePacks.size() > ResourcePackLimits.MAX_MANIFEST_ENTRIES) {
            throw new IllegalArgumentException("Too many resource-pack entries: " + resourcePacks.size());
        }
        for (String mod : modList) {
            if (mod == null || mod.isEmpty() || mod.length() > MAX_MOD_ENTRY_LENGTH) {
                throw new IllegalArgumentException("Invalid mod entry");
            }
        }
        for (ResourcePackManifestEntry pack : resourcePacks) {
            if (pack == null || pack.type() == null || pack.displayName() == null
                    || pack.displayName().isEmpty()
                    || pack.displayName().length() > ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH
                    || pack.sha256() == null || pack.sha256().length() > ResourcePackLimits.SHA256_LENGTH
                    || pack.identity() == null
                    || pack.identity().length() > ResourcePackLimits.MAX_PACK_IDENTITY_LENGTH
                    || pack.size() < 0) {
                throw new IllegalArgumentException("Invalid resource-pack manifest entry");
            }
        }
        modList = List.copyOf(modList);
        resourcePacks = List.copyOf(resourcePacks);
    }
}
