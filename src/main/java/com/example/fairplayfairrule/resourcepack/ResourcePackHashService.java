package com.example.fairplayfairrule.resourcepack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Raw whole-ZIP SHA-256 with bounded, identity-aware metadata caching. */
public final class ResourcePackHashService {
    private static final int DEFAULT_MAX_CACHE_ENTRIES = 256;
    private final HashComputer computer;
    private final Map<Path, CacheEntry> cache;

    public ResourcePackHashService() {
        this(DEFAULT_MAX_CACHE_ENTRIES, ResourcePackHashService::computeRawSha256);
    }

    public ResourcePackHashService(int maximumEntries, HashComputer computer) {
        if (maximumEntries <= 0) throw new IllegalArgumentException("maximumEntries must be positive");
        this.computer = java.util.Objects.requireNonNull(computer, "computer");
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Path, CacheEntry> eldest) {
                return size() > maximumEntries;
            }
        };
    }

    public synchronized HashResult hash(Path input) throws IOException { return hashInternal(input, true); }
    public synchronized HashResult hashUncached(Path input) throws IOException { return hashInternal(input, false); }

    private HashResult hashInternal(Path input, boolean useCache) throws IOException {
        Path path = normalize(input);
        for (int attempt = 0; attempt < 2; attempt++) {
            BasicFileAttributes before = attributes(path);
            if (useCache) {
                CacheEntry cached = cache.get(path);
                if (cached != null && cached.matches(before)
                        && cached.contentProbe.equals(contentProbe(path, before.size()))) {
                    BasicFileAttributes confirmed = attributes(path);
                    if (sameIdentityAndMetadata(before, confirmed)) {
                        return new HashResult(cached.sha256, before.size(),
                                before.lastModifiedTime().toMillis(), true);
                    }
                }
            }
            String sha256 = computer.hash(path);
            BasicFileAttributes after = attributes(path);
            if (sameIdentityAndMetadata(before, after)) {
                if (useCache) cache.put(path, new CacheEntry(after.size(),
                        after.lastModifiedTime(), after.creationTime(), after.fileKey(), sha256,
                        contentProbe(path, after.size())));
                return new HashResult(sha256, after.size(), after.lastModifiedTime().toMillis(), false);
            }
        }
        cache.remove(path);
        throw new IOException("Resource-pack file changed while it was being hashed");
    }

    private static Path normalize(Path input) throws IOException {
        if (input == null) throw new IOException("Resource-pack path is missing");
        Path path = input.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)) throw new IOException("Resource pack cannot be a symbolic link");
        Path noFollow = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path real = path.toRealPath();
        if (!noFollow.equals(real)) throw new IOException("Resource pack cannot be a link or junction");
        return real;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) throw new IOException("Resource pack is not a regular file");
        return attributes;
    }

    private static boolean sameIdentityAndMetadata(BasicFileAttributes first,
                                                   BasicFileAttributes second) {
        return first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime())
                && sameIdentity(first, second);
    }

    private static boolean sameIdentity(BasicFileAttributes first, BasicFileAttributes second) {
        if (first.fileKey() != null || second.fileKey() != null) {
            return java.util.Objects.equals(first.fileKey(), second.fileKey());
        }
        return first.creationTime().equals(second.creationTime());
    }

    private static String computeRawSha256(Path path) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
        try (SeekableByteChannel channel = Files.newByteChannel(path,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Small bounded replacement probe supplementing filesystem identity metadata. */
    private static String contentProbe(Path path, long size) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        int length = (int) Math.min(4_096L, size);
        ByteBuffer buffer = ByteBuffer.allocate(length);
        try (SeekableByteChannel channel = Files.newByteChannel(path,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
            buffer.flip();
            digest.update(buffer);
            if (size > length) {
                channel.position(Math.max(length, size - length));
                buffer.clear();
                while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
                buffer.flip();
                digest.update(buffer);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @FunctionalInterface
    public interface HashComputer { String hash(Path path) throws IOException; }

    public record HashResult(String sha256, long size, long lastModifiedMillis, boolean cacheHit) { }

    private record CacheEntry(long size, FileTime modified, FileTime created,
                              Object fileKey, String sha256, String contentProbe) {
        private boolean matches(BasicFileAttributes attributes) {
            boolean identityMatches = fileKey != null
                    ? fileKey.equals(attributes.fileKey())
                    : created.equals(attributes.creationTime());
            return size == attributes.size()
                    && modified.equals(attributes.lastModifiedTime())
                    && identityMatches;
        }
    }
}
