package com.example.fairplayfairrule.resourcepack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Opaque wire identity created only after a Forge pack origin has been proven trusted. */
public final class ModBundledIdentity {
    private static final String PREFIX = "mod-bundled:";
    private static final Pattern VALID = Pattern.compile("mod-bundled:[a-f0-9]{64}");

    private ModBundledIdentity() { }

    public static String fromTrustedProfileKey(String profileKey) {
        if (profileKey == null || profileKey.isBlank()
                || profileKey.length() > ResourcePackLimits.MAX_TRUSTED_PROFILE_KEY_LENGTH) {
            throw new IllegalArgumentException("Trusted profile key is missing or oversized");
        }
        try {
            return PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(profileKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static boolean isValid(String identity) {
        return identity != null && identity.length() <= ResourcePackLimits.MAX_PACK_IDENTITY_LENGTH
                && VALID.matcher(identity).matches();
    }
}
