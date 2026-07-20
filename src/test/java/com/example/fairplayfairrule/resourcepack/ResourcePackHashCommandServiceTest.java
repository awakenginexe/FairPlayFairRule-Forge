package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackHashCommandServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsPathLikeAndInvalidFileNamesBeforeScheduling() {
        try (ResourcePackHashCommandService service = service()) {
            for (String invalid : List.of(
                    "nested/pack.zip",
                    "nested\\pack.zip",
                    "pack..zip",
                    "C:pack.zip",
                    "C:\\pack.zip",
                    "/pack.zip",
                    "pack.txt",
                    "",
                    " ")) {
                ResourcePackHashCommandException exception = assertThrows(
                        ResourcePackHashCommandException.class,
                        () -> service.hash(invalid, null), invalid);
                assertFalse(exception.getMessage().contains(temporaryDirectory.toString()));
            }
        }
    }

    @Test
    void rejectsMalformedPlayerUuidBeforeScheduling() {
        try (ResourcePackHashCommandService service = service()) {
            ResourcePackHashCommandException exception = assertThrows(
                    ResourcePackHashCommandException.class,
                    () -> service.hash("pack.zip", "not-a-uuid"));

            assertEquals("Player UUID is invalid.", exception.getMessage());
        }
    }

    @Test
    void rejectsMissingInputDirectoryWithoutExposingItsPath() {
        Path missing = temporaryDirectory.resolve("missing");
        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(missing)) {
            ResourcePackHashCommandException exception = asyncFailure(service, "pack.zip");

            assertEquals("The administrator pack-hash input directory does not exist.",
                    exception.getMessage());
            assertFalse(exception.getMessage().contains(missing.toString()));
        }
    }

    @Test
    void rejectsDirectoriesAndMalformedZipFiles() throws Exception {
        Path input = createInputDirectory();
        Files.createDirectory(input.resolve("directory.zip"));
        Files.writeString(input.resolve("broken.zip"), "not a zip", StandardCharsets.UTF_8);

        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(input)) {
            assertEquals("The requested pack is not a regular file.",
                    asyncFailure(service, "directory.zip").getMessage());
            assertEquals("The requested file is not a valid ZIP resource pack.",
                    asyncFailure(service, "broken.zip").getMessage());
        }
    }

    @Test
    void rejectsCandidateSymlinkAndContainmentEscapeWhereSupported() throws Exception {
        Path input = createInputDirectory();
        Path outside = createZip(temporaryDirectory.resolve("outside.zip"));
        Path link = input.resolve("linked.zip");
        assumeSymlinkCreated(link, outside);

        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(input)) {
            ResourcePackHashCommandException exception = asyncFailure(service, "linked.zip");

            assertTrue(exception.getMessage().toLowerCase().contains("symbolic link"));
        }
    }

    @Test
    void rejectsSymlinkedApprovedInputDirectoryWhereSupported() throws Exception {
        Path realInput = Files.createDirectory(temporaryDirectory.resolve("real-input"));
        createZip(realInput.resolve("pack.zip"));
        Path linkedInput = temporaryDirectory.resolve("linked-input");
        assumeSymlinkCreated(linkedInput, realInput);

        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(linkedInput)) {
            ResourcePackHashCommandException exception = asyncFailure(service, "pack.zip");

            assertEquals("The administrator pack-hash input directory cannot be a symbolic link.",
                    exception.getMessage());
        }
    }

    @Test
    void hashesRawZipAndFormatsCopyReadyOutputWithoutPlayer() throws Exception {
        Path input = createInputDirectory();
        Path pack = createZip(input.resolve("Faithful32x.zip"));

        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(input)) {
            ResourcePackHashCommandResult result = service.hash("Faithful32x.zip", null).join();
            String expectedHash = rawSha256(Files.readAllBytes(pack));

            assertEquals("Faithful32x.zip", result.fileName());
            assertEquals(Files.size(pack), result.fileSize());
            assertEquals(expectedHash, result.sha256());
            assertEquals("File: Faithful32x.zip\n"
                            + "Size: " + Files.size(pack) + " bytes\n"
                            + "SHA-256: " + expectedHash + "\n"
                            + "Hash-list entry: \"" + expectedHash + "\"",
                    result.formattedOutput());
            assertFalse(result.formattedOutput().contains("Per-player entry:"));
        }
    }

    @Test
    void formatsCopyReadyPerPlayerEntryWhenUuidIsSupplied() throws Exception {
        Path input = createInputDirectory();
        createZip(input.resolve("PlayerPack.zip"));

        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(input)) {
            ResourcePackHashCommandResult result = service.hash(
                    "PlayerPack.zip", PLAYER_ID.toString()).join();

            assertTrue(result.formattedOutput().endsWith(
                    "Per-player entry: \"" + PLAYER_ID + "=" + result.sha256() + "\""));
        }
    }

    @Test
    void rehashesReplacementWithUnchangedSizeAndTimestamp() throws Exception {
        Path input = createInputDirectory();
        Path pack = createStoredZip(input.resolve("replace.zip"), new byte[]{1, 2, 3, 4});
        FileTime originalTime = Files.getLastModifiedTime(pack);

        try (ResourcePackHashCommandService service = new ResourcePackHashCommandService(input)) {
            String firstHash = service.hash("replace.zip", null).join().sha256();
            long originalSize = Files.size(pack);

            createStoredZip(pack, new byte[]{4, 3, 2, 1});
            assertEquals(originalSize, Files.size(pack));
            Files.setLastModifiedTime(pack, originalTime);

            String secondHash = service.hash("replace.zip", null).join().sha256();
            assertFalse(firstHash.equals(secondHash));
            assertEquals(rawSha256(Files.readAllBytes(pack)), secondHash);
        }
    }

    @Test
    void rejectsFileIdentityReplacementDuringHashing() throws Exception {
        Path input = createInputDirectory();
        Path pack = createStoredZip(input.resolve("racing.zip"), new byte[]{1, 2, 3, 4});
        Path replacement = createStoredZip(temporaryDirectory.resolve("replacement.zip"),
                new byte[]{4, 3, 2, 1});
        assertEquals(Files.size(pack), Files.size(replacement));
        FileTime originalTime = Files.getLastModifiedTime(pack);
        AtomicBoolean replaceOnce = new AtomicBoolean(true);
        ResourcePackHashService hashService = new ResourcePackHashService(8, path -> {
            byte[] bytes = Files.readAllBytes(path);
            if (replaceOnce.compareAndSet(true, false)) {
                Files.move(replacement, pack, StandardCopyOption.REPLACE_EXISTING);
                Files.setLastModifiedTime(pack, originalTime);
            }
            return rawSha256(bytes);
        });

        try (ResourcePackHashCommandService service =
                     new ResourcePackHashCommandService(input, hashService)) {
            ResourcePackHashCommandException exception = asyncFailure(service, "racing.zip");

            assertEquals("The requested pack changed while it was being hashed.",
                    exception.getMessage());
        }
    }

    @Test
    void stableSnapshotPreventsSymlinkSwapAndRestoreRedirectWhereSupported() throws Exception {
        Path input = createInputDirectory();
        Path pack = createStoredZip(input.resolve("stable.zip"), new byte[]{1, 2, 3, 4});
        Path outside = createStoredZip(temporaryDirectory.resolve("outside-stable.zip"),
                new byte[]{9, 8, 7, 6});
        Path backup = temporaryDirectory.resolve("stable-backup.zip");
        Path probe = input.resolve("symlink-probe.zip");
        assumeSymlinkCreated(probe, outside);
        Files.delete(probe);
        String expectedHash = rawSha256(Files.readAllBytes(pack));
        ResourcePackHashService hashService = new ResourcePackHashService(8, path -> {
            Files.move(pack, backup);
            try {
                Files.createSymbolicLink(pack, outside);
                return rawSha256(Files.readAllBytes(path));
            } finally {
                Files.deleteIfExists(pack);
                Files.move(backup, pack);
            }
        });

        try (ResourcePackHashCommandService service =
                     new ResourcePackHashCommandService(input, hashService)) {
            ResourcePackHashCommandResult result = service.hash("stable.zip", null).join();

            assertEquals(expectedHash, result.sha256());
            assertEquals(expectedHash, rawSha256(Files.readAllBytes(pack)));
        }
    }

    @Test
    void shutsDownBoundedWorkerAndRejectsFurtherWork() {
        ResourcePackHashCommandService service = service();
        service.close();

        assertThrows(ResourcePackHashCommandException.class,
                () -> service.hash("pack.zip", null));
    }

    private ResourcePackHashCommandService service() {
        return new ResourcePackHashCommandService(temporaryDirectory.resolve("input"));
    }

    private Path createInputDirectory() throws IOException {
        return Files.createDirectory(temporaryDirectory.resolve("input"));
    }

    private static Path createZip(Path path) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("pack.mcmeta"));
            output.write("{\"pack\":{}}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private static Path createStoredZip(Path path, byte[] contents) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(contents);
        ZipEntry entry = new ZipEntry("fixed.bin");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(contents.length);
        entry.setCompressedSize(contents.length);
        entry.setCrc(crc.getValue());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(entry);
            output.write(contents);
            output.closeEntry();
        }
        return path;
    }

    private static ResourcePackHashCommandException asyncFailure(
            ResourcePackHashCommandService service, String fileName) {
        CompletionException exception = assertThrows(CompletionException.class,
                () -> service.hash(fileName, null).join());
        assertTrue(exception.getCause() instanceof ResourcePackHashCommandException);
        return (ResourcePackHashCommandException) exception.getCause();
    }

    private static void assumeSymlinkCreated(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are not supported: " + exception.getClass().getSimpleName());
        }
    }

    private static String rawSha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
