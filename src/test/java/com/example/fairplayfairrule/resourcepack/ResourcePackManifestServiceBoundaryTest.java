package com.example.fairplayfairrule.resourcepack;

import com.example.fairplayfairrule.client.ResolvedResourcePack;
import com.example.fairplayfairrule.client.ResourcePackManifestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackManifestServiceBoundaryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void hashesCompleteUserZipAndKeepsOnlySafeFields() throws Exception {
        Path zip = createZip("Faithful.zip", "assets/example.txt", new byte[]{1, 2, 3});
        ResourcePackManifestService service = new ResourcePackManifestService(new ResourcePackHashService());

        ResourcePackManifestEntry entry = service.buildManifest(List.of(
                new ResolvedResourcePack("Faithful.zip", ResourcePackType.ZIP, zip))).get(0);

        assertEquals("Faithful.zip", entry.displayName());
        assertEquals(ResourcePackType.ZIP, entry.type());
        assertEquals(Files.size(zip), entry.size());
        assertEquals(64, entry.sha256().length());
        assertFalse(entry.toString().contains(zip.toAbsolutePath().toString()));
    }

    @Test
    void encodesBuiltInWithoutHashOrSize() {
        ResourcePackManifestEntry entry = service().buildManifest(List.of(
                new ResolvedResourcePack("Vanilla", ResourcePackType.BUILT_IN, null))).get(0);

        assertEquals(new ResourcePackManifestEntry("Vanilla", "", 0, ResourcePackType.BUILT_IN), entry);
    }

    @Test
    void preservesDirectoryAsUnsupportedManifestEntry() throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("DevelopmentPack"));

        ResourcePackManifestEntry entry = service().buildManifest(List.of(
                new ResolvedResourcePack("DevelopmentPack", ResourcePackType.DIRECTORY, directory))).get(0);

        assertEquals(ResourcePackType.DIRECTORY, entry.type());
        assertEquals("", entry.sha256());
        assertEquals(0, entry.size());
    }

    @Test
    void preservesUnresolvedWithoutLeakingLocalPath() {
        Path secret = temporaryDirectory.resolve("private").resolve("missing.zip");

        ResourcePackManifestEntry entry = service().buildManifest(List.of(
                new ResolvedResourcePack("Missing.zip", ResourcePackType.UNRESOLVED, secret))).get(0);

        assertEquals(ResourcePackType.UNRESOLVED, entry.type());
        assertFalse(entry.toString().contains(secret.toString()));
    }

    @Test
    void keepsDownloadedOriginSeparateWhileHashingRawZip() throws Exception {
        Path zip = createZip("server-pack.zip", "pack.mcmeta", new byte[]{9, 8, 7});

        ResourcePackManifestEntry entry = service().buildManifest(List.of(
                new ResolvedResourcePack("Server Resources", ResourcePackType.SERVER_DOWNLOADED, zip))).get(0);

        assertEquals(ResourcePackType.SERVER_DOWNLOADED, entry.type());
        assertEquals(64, entry.sha256().length());
        assertEquals(Files.size(zip), entry.size());
    }

    @Test
    void failedZipResolutionFailsClosedAsUnresolved() throws Exception {
        Path notZip = temporaryDirectory.resolve("broken.zip");
        Files.writeString(notZip, "not actually a zip");

        ResourcePackManifestEntry entry = service().buildManifest(List.of(
                new ResolvedResourcePack("broken.zip", ResourcePackType.ZIP, notZip))).get(0);

        assertEquals(ResourcePackType.UNRESOLVED, entry.type());
        assertEquals("", entry.sha256());
    }

    @Test
    void sanitizesDiagnosticNamesToPacketBound() {
        String unsafe = "line one\r\n" + "x".repeat(ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH + 50);

        ResourcePackManifestEntry entry = service().buildManifest(List.of(
                new ResolvedResourcePack(unsafe, ResourcePackType.BUILT_IN, null))).get(0);

        assertFalse(entry.displayName().contains("\r"));
        assertFalse(entry.displayName().contains("\n"));
        assertEquals(ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH, entry.displayName().length());
    }

    private ResourcePackManifestService service() {
        return new ResourcePackManifestService(new ResourcePackHashService());
    }

    private Path createZip(String name, String entryName, byte[] contents) throws IOException {
        Path zip = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents);
            output.closeEntry();
        }
        return zip;
    }
}
