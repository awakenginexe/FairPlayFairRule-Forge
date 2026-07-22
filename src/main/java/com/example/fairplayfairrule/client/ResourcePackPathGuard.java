package com.example.fairplayfairrule.client;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Real-path containment boundary for Minecraft-owned resource-pack files. */
public final class ResourcePackPathGuard {
    private ResourcePackPathGuard() { }

    public static Path contained(Path gameDirectory, Path input, boolean serverDownloaded) {
        try {
            Path candidate = input.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(candidate)
                    || !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return null;
            Path game = gameDirectory.toRealPath();
            List<Path> roots = serverDownloaded
                    ? List.of(game.resolve("server-resource-packs"), game.resolve("downloads"))
                    : List.of(game.resolve("resourcepacks"));
            for (Path root : roots) {
                Path normalizedRoot = root.toAbsolutePath().normalize();
                if (!candidate.startsWith(normalizedRoot) || candidate.equals(normalizedRoot)
                        || containsLinkOrJunction(normalizedRoot, candidate)) continue;
                Path realRoot = normalizedRoot.toRealPath();
                Path realCandidate = candidate.toRealPath();
                if (realCandidate.startsWith(realRoot) && !realCandidate.equals(realRoot)) {
                    return realCandidate;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean containsLinkOrJunction(Path root, Path candidate) {
        try {
            Path current = root;
            if (linkLike(current)) return true;
            for (Path part : root.relativize(candidate)) {
                current = current.resolve(part);
                if (linkLike(current)) return true;
            }
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private static boolean linkLike(Path path) throws java.io.IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) return true;
        return !path.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(path.toRealPath());
    }
}
