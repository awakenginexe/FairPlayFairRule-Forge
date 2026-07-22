package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.resourcepack.ModBundledIdentity;
import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.resource.ResourcePackLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.zip.ZipFile;

import static com.example.fairplayfairrule.client.TrustedResourcePackOriginClassifier.*;

/** Narrow Forge compatibility boundary for selected-pack source and backing identity. */
public final class ResourcePackResolver {
    private static final int MAX_REFLECTION_DEPTH = 5;
    private static final int MAX_REFLECTED_OBJECTS = 64;

    private ResourcePackResolver() { }

    public static ResolvedResourcePack resolve(Minecraft minecraft, Pack pack) {
        String id = pack.getId();
        String name = pack.getTitle().getString();
        PackSource source = pack.getPackSource();
        try (PackResources resources = pack.open()) {
            if (source == PackSource.BUILT_IN) {
                ImplementationOrigin implementation = isKnownMinecraftBuiltIn(resources)
                        ? ImplementationOrigin.VANILLA_BUILT_IN : ImplementationOrigin.UNKNOWN_VIRTUAL;
                ResourcePackType type = classify(ProfileOrigin.MINECRAFT_BUILT_IN, implementation);
                return new ResolvedResourcePack(name, type, null);
            }

            if (source == PackSource.SERVER) {
                Path path = findBackingPath(resources);
                Path safe = path == null ? null : ResourcePackPathGuard.contained(
                        minecraft.gameDirectory.toPath(), path, true);
                ImplementationOrigin implementation = safe != null && Files.isRegularFile(safe)
                        && isKnownZipImplementation(resources)
                        ? ImplementationOrigin.USER_ZIP : ImplementationOrigin.UNKNOWN_VIRTUAL;
                ResourcePackType type = classify(ProfileOrigin.SERVER, implementation);
                return new ResolvedResourcePack(name, type, type == ResourcePackType.SERVER_DOWNLOADED
                        ? safe : null);
            }

            Path userPath = resolveDirectUserPath(minecraft, id);
            if (userPath != null) {
                boolean directory = Files.isDirectory(userPath);
                ImplementationOrigin implementation = directory && isKnownDirectoryImplementation(resources)
                        ? ImplementationOrigin.USER_DIRECTORY
                        : !directory && Files.isRegularFile(userPath) && isKnownZipImplementation(resources)
                        ? ImplementationOrigin.USER_ZIP : ImplementationOrigin.UNKNOWN_VIRTUAL;
                ResourcePackType type = classify(ProfileOrigin.USER, implementation);
                return new ResolvedResourcePack(name, type,
                        type == ResourcePackType.ZIP || type == ResourcePackType.DIRECTORY
                                ? userPath : null);
            }

            if (isProvenForgeModPack(source, id, resources)) {
                ResourcePackType type = classify(ProfileOrigin.FORGE_MOD_BUNDLED,
                        ImplementationOrigin.FORGE_MOD);
                return new ResolvedResourcePack(name, type, null,
                        ModBundledIdentity.fromTrustedProfileKey(id));
            }
        } catch (Exception | LinkageError ignored) {
            // Every unavailable or unexpected implementation fails closed below.
        }
        return new ResolvedResourcePack(name, ResourcePackType.UNRESOLVED, null);
    }

    private static Path resolveDirectUserPath(Minecraft minecraft, String id) {
        if (id == null || !id.startsWith("file/")) return null;
        String fileName = id.substring("file/".length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) return null;
        Path candidate = minecraft.gameDirectory.toPath().resolve("resourcepacks").resolve(fileName);
        return ResourcePackPathGuard.contained(minecraft.gameDirectory.toPath(), candidate, false);
    }

    private static boolean isProvenForgeModPack(PackSource source, String id,
                                                PackResources resources) {
        if (source != PackSource.DEFAULT || id == null) return false;
        String className = resources.getClass().getName();
        if (className.equals("net.minecraftforge.resource.DelegatingResourcePack")
                || className.equals("net.minecraftforge.resource.DelegatingPackResources")) {
            return "mod_resources".equals(id);
        }
        Class<?> parent = resources.getClass().getSuperclass();
        boolean forgeLoaderAnonymous = className.startsWith(
                "net.minecraftforge.resource.ResourcePackLoader$") && parent != null
                && (parent.getName().equals("net.minecraftforge.resource.PathResourcePack")
                || parent.getName().equals("net.minecraftforge.resource.PathPackResources"));
        if (forgeLoaderAnonymous) return registeredForgePackId(id);
        return className.equals("net.minecraft.server.packs.PathPackResources")
                && registeredForgePackId(id);
    }

    private static boolean registeredForgePackId(String id) {
        try {
            return ResourcePackLoader.getPackNames().contains(id);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static boolean isKnownMinecraftBuiltIn(PackResources resources) {
        String name = resources.getClass().getName();
        return name.equals("net.minecraft.server.packs.VanillaPackResources")
                || name.equals("net.minecraft.server.packs.PathPackResources")
                || name.equals("net.minecraft.server.packs.FilePackResources")
                || name.equals("net.minecraft.server.packs.FolderPackResources")
                || name.startsWith("net.minecraft.client.resources.ClientPackSource$");
    }

    private static boolean isKnownZipImplementation(PackResources resources) {
        String name = resources.getClass().getName();
        return name.equals("net.minecraft.server.packs.FilePackResources")
                || name.equals("net.minecraft.server.packs.FileResourcePack")
                || name.startsWith("net.minecraft.server.packs.FilePackResources$");
    }

    private static boolean isKnownDirectoryImplementation(PackResources resources) {
        String name = resources.getClass().getName();
        return name.equals("net.minecraft.server.packs.PathPackResources")
                || name.equals("net.minecraft.server.packs.FolderPackResources")
                || name.equals("net.minecraft.server.packs.FolderResourcePack");
    }

    private static Path findBackingPath(Object resources) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findPath(resources, 0, visited, new int[]{0});
    }

    private static Path findPath(Object value, int depth, Set<Object> visited, int[] inspected) {
        if (value == null || depth > MAX_REFLECTION_DEPTH
                || inspected[0]++ >= MAX_REFLECTED_OBJECTS || !visited.add(value)) return null;
        if (value instanceof Path path) return path;
        if (value instanceof File file) return file.toPath();
        if (value instanceof ZipFile zipFile) return Path.of(zipFile.getName());
        String packageName = value.getClass().getPackageName();
        if (!packageName.startsWith("net.minecraft") && !packageName.startsWith("net.minecraftforge")) {
            return null;
        }
        for (Class<?> current = value.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    if (!field.trySetAccessible()) continue;
                    Path found = findPath(field.get(value), depth + 1, visited, inspected);
                    if (found != null) return found;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return null;
    }
}
