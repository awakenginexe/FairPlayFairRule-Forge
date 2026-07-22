package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackHashServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void computesRawSha256OfCompleteFile() throws Exception {
        Path pack = temporaryDirectory.resolve("pack.zip");
        byte[] completeFile = "not canonical zip contents".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(pack, completeFile);

        ResourcePackHashService.HashResult result = new ResourcePackHashService().hash(pack);

        assertEquals(sha256(completeFile), result.sha256());
        assertEquals(completeFile.length, result.size());
        assertFalse(result.cacheHit());
    }

    @Test
    void reusesHashWhenNormalizedPathSizeAndTimestampAreUnchanged() throws Exception {
        Path pack = temporaryDirectory.resolve("folder").resolve("..").resolve("pack.zip");
        Files.createDirectories(pack.getParent());
        Files.write(pack, new byte[]{1, 2, 3});
        AtomicInteger reads = new AtomicInteger();
        ResourcePackHashService service = new ResourcePackHashService(8, path -> {
            reads.incrementAndGet();
            return sha256(Files.readAllBytes(path));
        });

        ResourcePackHashService.HashResult first = service.hash(pack);
        ResourcePackHashService.HashResult second = service.hash(pack.toAbsolutePath().normalize());

        assertFalse(first.cacheHit());
        assertTrue(second.cacheHit());
        assertEquals(1, reads.get());
        assertEquals(first.sha256(), second.sha256());
    }

    @Test
    void invalidatesCacheWhenSizeChanges() throws Exception {
        Path pack = temporaryDirectory.resolve("pack.zip");
        Files.write(pack, new byte[]{1});
        AtomicInteger reads = new AtomicInteger();
        ResourcePackHashService service = countingService(reads);
        ResourcePackHashService.HashResult first = service.hash(pack);

        Files.write(pack, new byte[]{1, 2});
        ResourcePackHashService.HashResult second = service.hash(pack);

        assertFalse(second.cacheHit());
        assertEquals(2, reads.get());
        assertNotEquals(first.sha256(), second.sha256());
    }

    @Test
    void invalidatesCacheWhenTimestampChangesEvenAtSameSize() throws Exception {
        Path pack = temporaryDirectory.resolve("pack.zip");
        Files.write(pack, new byte[]{1, 2, 3});
        AtomicInteger reads = new AtomicInteger();
        ResourcePackHashService service = countingService(reads);
        ResourcePackHashService.HashResult first = service.hash(pack);
        FileTime changedTime = FileTime.fromMillis(first.lastModifiedMillis() + 5_000);

        Files.write(pack, new byte[]{3, 2, 1});
        Files.setLastModifiedTime(pack, changedTime);
        ResourcePackHashService.HashResult second = service.hash(pack);

        assertFalse(second.cacheHit());
        assertEquals(2, reads.get());
        assertNotEquals(first.sha256(), second.sha256());
    }

    @Test
    void invalidatesForSubMillisecondTimestampChangeAtSameSize() throws Exception {
        Path pack = temporaryDirectory.resolve("precise.zip");
        Files.write(pack, new byte[]{1, 2, 3});
        AtomicInteger reads = new AtomicInteger();
        ResourcePackHashService service = countingService(reads);
        service.hash(pack);
        FileTime original = Files.getLastModifiedTime(pack);
        FileTime preciseChange = FileTime.from(original.toInstant().plusNanos(100));

        Files.write(pack, new byte[]{3, 2, 1});
        Files.setLastModifiedTime(pack, preciseChange);
        FileTime storedChange = Files.getLastModifiedTime(pack);
        assertNotEquals(original, storedChange, "Filesystem must preserve a finer timestamp for this test");
        assertEquals(original.toMillis(), storedChange.toMillis());

        ResourcePackHashService.HashResult result = service.hash(pack);

        assertFalse(result.cacheHit());
        assertEquals(2, reads.get());
    }

    @Test
    void modifyingAnyByteChangesRawDigest() throws Exception {
        Path pack = temporaryDirectory.resolve("pack.zip");
        Files.write(pack, new byte[]{10, 20, 30, 40});
        ResourcePackHashService service = new ResourcePackHashService();
        String before = service.hash(pack).sha256();

        Files.write(pack, new byte[]{10, 20, 31, 40});
        Files.setLastModifiedTime(pack, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        String after = service.hash(pack).sha256();

        assertNotEquals(before, after);
    }

    @Test
    void sameSizeSameTimestampReplacementDoesNotReuseCacheWhenFileIdentityIsAvailable() throws Exception {
        Path pack = temporaryDirectory.resolve("pack.zip");
        Files.write(pack, new byte[]{1, 2, 3, 4});
        FileTime timestamp = Files.getLastModifiedTime(pack);
        AtomicInteger reads = new AtomicInteger();
        ResourcePackHashService service = countingService(reads);
        String before = service.hash(pack).sha256();
        Path replacement = temporaryDirectory.resolve("replacement.zip");
        Files.write(replacement, new byte[]{4, 3, 2, 1});
        Files.setLastModifiedTime(replacement, timestamp);
        Files.move(replacement, pack, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(pack, timestamp);

        ResourcePackHashService.HashResult after = service.hash(pack);
        assertFalse(after.cacheHit());
        assertNotEquals(before, after.sha256());
        assertEquals(2, reads.get());
    }

    @Test
    void rejectsSymbolicLinksWhenSupported() throws Exception {
        Path target = Files.write(temporaryDirectory.resolve("target.zip"), new byte[]{1});
        Path link = temporaryDirectory.resolve("link.zip");
        try {
            Files.createSymbolicLink(link, target);
            assertThrows(IOException.class, () -> new ResourcePackHashService().hash(link));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // Link behavior is exercised on filesystems where the test process may create one.
        }
    }

    private static ResourcePackHashService countingService(AtomicInteger reads) {
        return new ResourcePackHashService(8, path -> {
            reads.incrementAndGet();
            return sha256(Files.readAllBytes(path));
        });
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
