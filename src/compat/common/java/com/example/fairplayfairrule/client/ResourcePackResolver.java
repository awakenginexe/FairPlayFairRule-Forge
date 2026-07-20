package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Compatibility boundary for resolving Minecraft's selected Pack abstraction.
 * Reflection is deliberately bounded and confined to this adapter.
 */
public final class ResourcePackResolver {
    private static final int MAX_REFLECTION_DEPTH = 5;
    private static final int MAX_REFLECTED_OBJECTS = 64;

    private ResourcePackResolver() {
    }

    public static ResolvedResourcePack resolve(Minecraft minecraft, Pack pack) {
        String id = pack.getId();
        String name = pack.getTitle().getString();

        if (pack.getPackSource() == PackSource.SERVER) {
            Path downloaded = resolveDownloadedPath(pack);
            return new ResolvedResourcePack(name, downloaded == null
                    ? ResourcePackType.UNRESOLVED
                    : ResourcePackType.SERVER_DOWNLOADED, downloaded);
        }

        if (isBuiltIn(pack, id)) {
            return new ResolvedResourcePack(name, ResourcePackType.BUILT_IN, null);
        }

        if (id != null && id.startsWith("file/")) {
            Path root = minecraft.gameDirectory.toPath().resolve("resourcepacks")
                    .toAbsolutePath().normalize();
            String relativeName = id.substring("file/".length());
            Path candidate = root.resolve(relativeName).toAbsolutePath().normalize();
            if (!candidate.startsWith(root) || candidate.equals(root)) {
                return new ResolvedResourcePack(name, ResourcePackType.UNRESOLVED, null);
            }
            if (Files.isDirectory(candidate)) {
                return new ResolvedResourcePack(name, ResourcePackType.DIRECTORY, candidate);
            }
            if (Files.isRegularFile(candidate)) {
                return new ResolvedResourcePack(name, ResourcePackType.ZIP, candidate);
            }
            return new ResolvedResourcePack(name, ResourcePackType.UNRESOLVED, null);
        }

        return new ResolvedResourcePack(name, ResourcePackType.UNRESOLVED, null);
    }

    private static boolean isBuiltIn(Pack pack, String id) {
        return pack.getPackSource() == PackSource.BUILT_IN
                || "vanilla".equals(id)
                || "mod_resources".equals(id)
                || (id != null && id.startsWith("mod:"));
    }

    private static Path resolveDownloadedPath(Pack pack) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] inspected = {0};
        try (PackResources resources = pack.open()) {
            Path path = findPath(resources, 0, visited, inspected);
            if (path == null) {
                return null;
            }
            Path normalized = path.toAbsolutePath().normalize();
            return Files.isRegularFile(normalized) ? normalized : null;
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    private static Path findPath(Object value, int depth, Set<Object> visited, int[] inspected) {
        if (value == null || depth > MAX_REFLECTION_DEPTH
                || inspected[0]++ >= MAX_REFLECTED_OBJECTS || !visited.add(value)) {
            return null;
        }
        if (value instanceof Path path) {
            return path;
        }
        if (value instanceof File file) {
            return file.toPath();
        }
        if (value instanceof ZipFile zipFile) {
            return Path.of(zipFile.getName());
        }

        Class<?> type = value.getClass();
        String packageName = type.getPackageName();
        if (!packageName.startsWith("net.minecraft") && !packageName.startsWith("net.minecraftforge")) {
            return null;
        }
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    if (!field.trySetAccessible()) {
                        continue;
                    }
                    Path found = findPath(field.get(value), depth + 1, visited, inspected);
                    if (found != null) {
                        return found;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Fail closed if a version prevents inspection of this field.
                }
            }
        }
        return null;
    }
}
