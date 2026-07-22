package com.example.fairplayfairrule.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackPathGuardTest {
    @TempDir Path root;

    @Test
    void acceptsOnlyFilesUnderTheCorrectRealRoot() throws Exception {
        Path userRoot = Files.createDirectories(root.resolve("resourcepacks"));
        Path user = Files.writeString(userRoot.resolve("vanilla.zip"), "zip");
        Path serverRoot = Files.createDirectories(root.resolve("server-resource-packs"));
        Path server = Files.writeString(serverRoot.resolve("download.zip"), "zip");
        Path outside = Files.writeString(root.resolve("outside.zip"), "zip");

        assertEquals(user.toRealPath(), ResourcePackPathGuard.contained(root, user, false));
        assertEquals(server.toRealPath(), ResourcePackPathGuard.contained(root, server, true));
        assertNull(ResourcePackPathGuard.contained(root, user, true));
        assertNull(ResourcePackPathGuard.contained(root, outside, false));
    }

    @Test
    void rejectsDirectoryAndSymbolicLinkWhenSupported() throws Exception {
        Path userRoot = Files.createDirectories(root.resolve("resourcepacks"));
        assertNull(ResourcePackPathGuard.contained(root, userRoot, false));
        Path target = Files.writeString(userRoot.resolve("target.zip"), "zip");
        Path link = userRoot.resolve("link.zip");
        try {
            Files.createSymbolicLink(link, target);
            assertNull(ResourcePackPathGuard.contained(root, link, false));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // The containment behavior remains covered on platforms permitting links.
        }
    }
}
