package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
