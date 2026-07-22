package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.resourcepack.ResourcePackHashService;
import com.example.fairplayfairrule.resourcepack.ResourcePackLimits;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import com.example.fairplayfairrule.resourcepack.ModBundledIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

/** Converts client-local pack resolutions into safe manifest entries. */
public final class ResourcePackManifestService {
    private final ResourcePackHashService hashService;

    public ResourcePackManifestService(ResourcePackHashService hashService) {
        this.hashService = Objects.requireNonNull(hashService, "hashService");
    }

    /** Snapshot selected packs and resolve their origins on the client thread. */
    public List<ResolvedResourcePack> resolveSelectedPacks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourcePackRepository() == null) {
            return List.of(new ResolvedResourcePack(
                    "Resource-pack repository unavailable", ResourcePackType.UNRESOLVED, null));
        }
        List<ResolvedResourcePack> resolved = new ArrayList<>();
        for (Pack pack : minecraft.getResourcePackRepository().getSelectedPacks()) {
            resolved.add(ResourcePackResolver.resolve(minecraft, pack));
        }
        return List.copyOf(resolved);
    }

    public List<ResourcePackManifestEntry> buildManifest(List<ResolvedResourcePack> packs) {
        if (packs == null || packs.size() > ResourcePackLimits.MAX_MANIFEST_ENTRIES) {
            return List.of(new ResourcePackManifestEntry(
                    "Resource-pack selection is missing or oversized", "", 0,
                    ResourcePackType.UNRESOLVED));
        }
        List<ResourcePackManifestEntry> manifest = new ArrayList<>(packs.size());
        for (ResolvedResourcePack pack : packs) {
            manifest.add(buildEntry(pack));
        }
        return List.copyOf(manifest);
    }

    private ResourcePackManifestEntry buildEntry(ResolvedResourcePack pack) {
        if (pack == null) {
            return unresolved("Unknown resource pack");
        }
        String name = safeName(pack.displayName());
        ResourcePackType type = pack.type();
        if (type == ResourcePackType.BUILT_IN) {
            return new ResourcePackManifestEntry(name, "", 0, ResourcePackType.BUILT_IN);
        }
        if (type == ResourcePackType.MOD_BUNDLED) {
            return ModBundledIdentity.isValid(pack.identity())
                    ? new ResourcePackManifestEntry(name, "", 0,
                    ResourcePackType.MOD_BUNDLED, pack.identity())
                    : unresolved(name);
        }
        if (type == ResourcePackType.DIRECTORY) {
            return new ResourcePackManifestEntry(name, "", 0, ResourcePackType.DIRECTORY);
        }
        if (type == ResourcePackType.UNRESOLVED || type == null || pack.localPath() == null) {
            return unresolved(name);
        }

        Path path = pack.localPath().toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            return new ResourcePackManifestEntry(name, "", 0, ResourcePackType.DIRECTORY);
        }
        if (!isReadableZip(path)) {
            return unresolved(name);
        }
        try {
            ResourcePackHashService.HashResult hash = hashService.hash(path);
            return new ResourcePackManifestEntry(name, hash.sha256(), hash.size(), type);
        } catch (IOException | RuntimeException exception) {
            return unresolved(name);
        }
    }

    private static boolean isReadableZip(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try (ZipFile ignored = new ZipFile(path.toFile())) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static ResourcePackManifestEntry unresolved(String name) {
        return new ResourcePackManifestEntry(name, "", 0, ResourcePackType.UNRESOLVED);
    }

    private static String safeName(String name) {
        String safe = name == null || name.isBlank() ? "Unnamed resource pack" : name;
        safe = safe.replace('\r', ' ').replace('\n', ' ');
        if (safe.length() > ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH) {
            safe = safe.substring(0, ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH);
        }
        return safe;
    }
}
