package com.example.fairplayfairrule.server;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Produces the exact UTF-8 diagnostic text delivered as Discord attachments. */
public final class DiscordManifestTextBuilder {
    private DiscordManifestTextBuilder() {
    }

    public static byte[] playerManifest(List<String> mods, List<String> packs) {
        StringBuilder text = new StringBuilder();
        appendManifest(text, mods, packs);
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] banManifest(String bannedModId, List<String> mods, List<String> packs) {
        StringBuilder text = new StringBuilder();
        text.append("=== BANNED PLAYER INFO ===\n")
                .append("Banned for mod: ").append(Objects.requireNonNull(bannedModId, "bannedModId"))
                .append("\n\n");
        appendManifest(text, mods, packs);
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendManifest(StringBuilder text, List<String> mods, List<String> packs) {
        Objects.requireNonNull(mods, "mods");
        Objects.requireNonNull(packs, "packs");
        text.append("=== MODS (").append(mods.size()).append(" total) ===\n");
        for (String mod : mods) {
            text.append(Objects.requireNonNull(mod, "mod entry")).append('\n');
        }
        text.append("\n=== RESOURCE PACKS (").append(packs.size()).append(" enabled) ===\n");
        for (String pack : packs) {
            text.append(Objects.requireNonNull(pack, "pack entry")).append('\n');
        }
    }
}
