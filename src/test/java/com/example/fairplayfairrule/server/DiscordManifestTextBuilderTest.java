package com.example.fairplayfairrule.server;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DiscordManifestTextBuilderTest {
    @Test
    void regularManifestHasExactUtf8Content() {
        byte[] actual = DiscordManifestTextBuilder.playerManifest(
                List.of("alpha@1.0", "snowman-☃@2"),
                List.of("Pack é | sha256=abc"));

        assertArrayEquals(("=== MODS (2 total) ===\n"
                + "alpha@1.0\n"
                + "snowman-☃@2\n"
                + "\n=== RESOURCE PACKS (1 enabled) ===\n"
                + "Pack é | sha256=abc\n").getBytes(StandardCharsets.UTF_8), actual);
    }

    @Test
    void banManifestPreservesExistingDiagnosticContent() {
        byte[] actual = DiscordManifestTextBuilder.banManifest(
                "badmod", List.of("badmod@3"), List.of("pack.zip"));

        assertArrayEquals(("=== BANNED PLAYER INFO ===\n"
                + "Banned for mod: badmod\n\n"
                + "=== MODS (1 total) ===\n"
                + "badmod@3\n"
                + "\n=== RESOURCE PACKS (1 enabled) ===\n"
                + "pack.zip\n").getBytes(StandardCharsets.UTF_8), actual);
    }
}
