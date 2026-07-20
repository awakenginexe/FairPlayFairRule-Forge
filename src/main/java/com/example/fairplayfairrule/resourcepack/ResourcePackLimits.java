package com.example.fairplayfairrule.resourcepack;

/** Shared client, codec, configuration, and validation bounds. */
public final class ResourcePackLimits {
    public static final int MAX_MANIFEST_ENTRIES = 128;
    public static final int MAX_DISPLAY_NAME_LENGTH = 160;
    public static final int SHA256_LENGTH = 64;
    public static final int MAX_POLICY_HASHES = 512;
    public static final int MAX_PLAYER_POLICY_ENTRIES = 2_048;

    private ResourcePackLimits() {
    }
}
