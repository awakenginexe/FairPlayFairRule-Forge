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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Safely hashes administrator-provided ZIPs on one bounded background worker. */
public final class ResourcePackHashCommandService implements AutoCloseable {
    private static final int MAX_PENDING_REQUESTS = 8;

    private final Path inputDirectory;
    private final ResourcePackHashService hashService;
    private final ThreadPoolExecutor worker;

    public ResourcePackHashCommandService(Path inputDirectory) {
        this(inputDirectory, new ResourcePackHashService());
    }

    ResourcePackHashCommandService(Path inputDirectory, ResourcePackHashService hashService) {
        this.inputDirectory = Objects.requireNonNull(inputDirectory, "inputDirectory")
                .toAbsolutePath().normalize();
        this.hashService = Objects.requireNonNull(hashService, "hashService");
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_REQUESTS),
                runnable -> {
                    Thread thread = new Thread(runnable, "fpfr-pack-hash-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public CompletableFuture<ResourcePackHashCommandResult> hash(String fileName, String playerUuid) {
        validateFileName(fileName);
        UUID playerId = parsePlayerUuid(playerUuid);
        if (worker.isShutdown()) {
            throw new ResourcePackHashCommandException("The resource-pack hash worker is not running.");
        }

        CompletableFuture<ResourcePackHashCommandResult> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(hashOnWorker(fileName, playerId));
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            throw new ResourcePackHashCommandException(
                    "The resource-pack hash queue is full; wait for an earlier request to finish.");
        }
        return future;
    }

    private ResourcePackHashCommandResult hashOnWorker(String fileName, UUID playerId) {
        Path zip = resolveApprovedZip(fileName);
        FileIdentity sourceIdentity = readIdentity(zip);
        Path snapshot = captureSnapshot(zip, sourceIdentity);
        Path confirmationSnapshot = null;
        try {
            ResourcePackHashService.HashResult result = hashSnapshot(snapshot);
            confirmationSnapshot = captureSnapshot(zip, sourceIdentity);
            ResourcePackHashService.HashResult confirmation = hashSnapshot(confirmationSnapshot);
            if (result.size() != confirmation.size()
                    || !result.sha256().equals(confirmation.sha256())) {
                throw new ResourcePackHashCommandException(
                        "The requested pack changed while it was being hashed.");
            }
            return new ResourcePackHashCommandResult(
                    fileName,
                    result.size(),
                    result.sha256().toLowerCase(Locale.ROOT),
                    playerId);
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException("The requested ZIP file could not be read.");
        } finally {
            deleteSnapshot(snapshot);
            if (confirmationSnapshot != null) {
                deleteSnapshot(confirmationSnapshot);
            }
        }
    }

    private ResourcePackHashService.HashResult hashSnapshot(Path snapshot) throws IOException {
        FileIdentity snapshotIdentity = readIdentity(snapshot);
        validateZip(snapshot);
        verifySnapshot(snapshot, snapshotIdentity);
        ResourcePackHashService.HashResult result = hashService.hashUncached(snapshot);
        verifySnapshot(snapshot, snapshotIdentity);
        validateZip(snapshot);
        return result;
    }

    private Path captureSnapshot(Path source, FileIdentity expectedIdentity) {
        Path snapshot;
        try {
            snapshot = Files.createTempFile("fpfr-packhash-", ".zip");
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException(
                    "A secure temporary hash snapshot could not be created.");
        }

        boolean complete = false;
        try (SeekableByteChannel input = Files.newByteChannel(
                source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             SeekableByteChannel output = Files.newByteChannel(
                     snapshot, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long expectedSize = input.size();
            long copied = 0;
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Hash worker interrupted");
                }
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                copied += read;
                buffer.flip();
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                buffer.clear();
            }
            if (copied != expectedSize || input.size() != expectedSize) {
                throw new ResourcePackHashCommandException(
                        "The requested pack changed while it was being captured.");
            }
            verifyStillApproved(source, expectedIdentity);
            complete = true;
            return snapshot;
        } catch (ResourcePackHashCommandException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException(
                    "The requested ZIP file could not be captured safely.");
        } finally {
            if (!complete) {
                deleteSnapshot(snapshot);
            }
        }
    }

    private Path resolveApprovedZip(String fileName) {
        if (!Files.exists(inputDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourcePackHashCommandException(
                    "The administrator pack-hash input directory does not exist.");
        }
        if (Files.isSymbolicLink(inputDirectory)) {
            throw new ResourcePackHashCommandException(
                    "The administrator pack-hash input directory cannot be a symbolic link.");
        }
        if (!Files.isDirectory(inputDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourcePackHashCommandException(
                    "The administrator pack-hash input location is not a directory.");
        }

        Path candidate = inputDirectory.resolve(fileName).normalize();
        if (!inputDirectory.equals(candidate.getParent())) {
            throw new ResourcePackHashCommandException("Only a direct-child ZIP file name is allowed.");
        }
        if (Files.isSymbolicLink(candidate)) {
            throw new ResourcePackHashCommandException("Symbolic links are not allowed.");
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourcePackHashCommandException("The requested pack is not a regular file.");
        }

        try {
            Path realRoot = inputDirectory.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot) || !realRoot.equals(realCandidate.getParent())) {
                throw new ResourcePackHashCommandException(
                        "The requested pack is outside the approved input directory.");
            }
            return realCandidate;
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException("The requested pack could not be resolved safely.");
        }
    }

    private void verifyStillApproved(Path original, FileIdentity expectedIdentity) {
        if (Files.isSymbolicLink(inputDirectory) || Files.isSymbolicLink(inputDirectory.resolve(original.getFileName()))) {
            throw new ResourcePackHashCommandException("Symbolic links are not allowed.");
        }
        try {
            Path realRoot = inputDirectory.toRealPath();
            Path current = inputDirectory.resolve(original.getFileName()).toRealPath();
            if (!current.equals(original) || !realRoot.equals(current.getParent())
                    || !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                    || !expectedIdentity.equals(readIdentity(current))) {
                throw new ResourcePackHashCommandException(
                        "The requested pack changed while it was being hashed.");
            }
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException(
                    "The requested pack changed while it was being hashed.");
        }
    }

    private static FileIdentity readIdentity(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new ResourcePackHashCommandException("The requested pack is not a regular file.");
            }
            return new FileIdentity(
                    attributes.fileKey(),
                    attributes.size(),
                    attributes.lastModifiedTime(),
                    attributes.creationTime());
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException("The requested pack could not be resolved safely.");
        }
    }

    private static void verifySnapshot(Path snapshot, FileIdentity expectedIdentity) {
        if (Files.isSymbolicLink(snapshot) || !expectedIdentity.equals(readIdentity(snapshot))) {
            throw new ResourcePackHashCommandException(
                    "The secure hash snapshot changed unexpectedly.");
        }
    }

    private static void deleteSnapshot(Path snapshot) {
        try {
            Files.deleteIfExists(snapshot);
        } catch (IOException exception) {
            snapshot.toFile().deleteOnExit();
        }
    }

    private static void validateZip(Path zip) {
        try (ZipFile ignored = new ZipFile(zip.toFile())) {
            // Opening the central directory is enough to reject non-ZIP input.
        } catch (ZipException exception) {
            throw new ResourcePackHashCommandException(
                    "The requested file is not a valid ZIP resource pack.");
        } catch (IOException exception) {
            throw new ResourcePackHashCommandException("The requested ZIP file could not be read.");
        }
    }

    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..") || fileName.contains(":")) {
            throw new ResourcePackHashCommandException("Only a direct-child ZIP file name is allowed.");
        }
        Path name;
        try {
            name = Path.of(fileName);
        } catch (RuntimeException exception) {
            throw new ResourcePackHashCommandException("Only a direct-child ZIP file name is allowed.");
        }
        if (name.isAbsolute() || name.getNameCount() != 1
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ResourcePackHashCommandException("Only a direct-child ZIP file name is allowed.");
        }
    }

    private static UUID parsePlayerUuid(String playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        String value = playerUuid.trim();
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("UUID is not canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new ResourcePackHashCommandException("Player UUID is invalid.");
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }

    private record FileIdentity(Object fileKey, long size, FileTime lastModified, FileTime created) {
    }
}
