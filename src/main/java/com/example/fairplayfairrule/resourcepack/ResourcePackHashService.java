package com.example.fairplayfairrule.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Computes raw whole-file SHA-256 values with a bounded metadata cache. */
public final class ResourcePackHashService {
    private static final int DEFAULT_MAX_CACHE_ENTRIES = 256;
    private final HashComputer computer;
    private final Map<Path, CacheEntry> cache;

    public ResourcePackHashService() {
        this(DEFAULT_MAX_CACHE_ENTRIES, ResourcePackHashService::computeRawSha256);
    }

    public ResourcePackHashService(int maximumEntries, HashComputer computer) {
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.computer = java.util.Objects.requireNonNull(computer, "computer");
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, CacheEntry> eldest) {
                return size() > maximumEntries;
            }
        };
    }

    public synchronized HashResult hash(Path input) throws IOException {
        return hashInternal(input, true);
    }

    /** Computes a fresh digest without consulting or updating the metadata cache. */
    public synchronized HashResult hashUncached(Path input) throws IOException {
        return hashInternal(input, false);
    }

    private HashResult hashInternal(Path input, boolean useCache) throws IOException {
        Path path = input.toAbsolutePath().normalize();
        for (int attempt = 0; attempt < 2; attempt++) {
            BasicFileAttributes before = readAttributes(path);
            if (useCache) {
                CacheEntry cached = cache.get(path);
                if (cached != null && cached.matches(before)) {
                    return new HashResult(cached.sha256, before.size(),
                            before.lastModifiedTime().toMillis(), true);
                }
            }

            String sha256 = computer.hash(path);
            BasicFileAttributes after = readAttributes(path);
            if (sameMetadata(before, after)) {
                if (useCache) {
                    cache.put(path, new CacheEntry(after.size(),
                            after.lastModifiedTime(), sha256));
                }
                return new HashResult(sha256, after.size(),
                        after.lastModifiedTime().toMillis(), false);
            }
        }
        throw new IOException("Resource-pack file changed while it was being hashed");
    }

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) {
            throw new IOException("Resource pack is not a regular ZIP file");
        }
        return attributes;
    }

    private static boolean sameMetadata(BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime());
    }

    private static String computeRawSha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @FunctionalInterface
    public interface HashComputer {
        String hash(Path path) throws IOException;
    }

    public record HashResult(String sha256, long size, long lastModifiedMillis, boolean cacheHit) {
    }

    private record CacheEntry(long size, FileTime lastModified, String sha256) {
        private boolean matches(BasicFileAttributes attributes) {
            return size == attributes.size()
                    && lastModified.equals(attributes.lastModifiedTime());
        }
    }
}
