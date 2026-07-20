package com.example.fairplayfairrule.network;

import com.example.fairplayfairrule.resourcepack.ResourcePackLimits;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientInfoPayloadValidationTest {
    @Test
    void defensivelyCopiesLists() {
        List<String> mods = new ArrayList<>(List.of("example@1.0"));
        List<ResourcePackManifestEntry> packs = new ArrayList<>(List.of(builtIn()));

        ClientInfoPayload payload = new ClientInfoPayload(ResourcePackReportType.JOIN, mods, packs);
        mods.clear();
        packs.clear();

        assertEquals(List.of("example@1.0"), payload.modList());
        assertEquals(List.of(builtIn()), payload.resourcePacks());
        assertThrows(UnsupportedOperationException.class, () -> payload.modList().add("other@1"));
    }

    @Test
    void rejectsOversizedListsAndFields() {
        List<String> tooManyMods = java.util.Collections.nCopies(ClientInfoPayload.MAX_MOD_ENTRIES + 1, "m@1");
        List<ResourcePackManifestEntry> tooManyPacks = java.util.Collections.nCopies(
                ResourcePackLimits.MAX_MANIFEST_ENTRIES + 1, builtIn());

        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN, tooManyMods, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN, List.of(), tooManyPacks));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN,
                        List.of("m".repeat(ClientInfoPayload.MAX_MOD_ENTRY_LENGTH + 1)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN, List.of(), List.of(
                        new ResourcePackManifestEntry(
                                "x".repeat(ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH + 1), "", 0,
                                ResourcePackType.BUILT_IN))));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN, List.of(), List.of(
                        new ResourcePackManifestEntry("pack", "a".repeat(65), 1, ResourcePackType.ZIP))));
    }

    @Test
    void rejectsNegativeSizeAndMissingTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN, List.of(), List.of(
                        new ResourcePackManifestEntry("pack", "", -1, ResourcePackType.UNRESOLVED))));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientInfoPayload(ResourcePackReportType.JOIN, List.of(), List.of(
                        new ResourcePackManifestEntry("pack", "", 0, null))));
    }

    @Test
    void rejectsUnknownReportAndPackNetworkIds() {
        assertThrows(IllegalArgumentException.class, () -> ResourcePackReportType.fromNetworkId(99));
        assertThrows(IllegalArgumentException.class, () -> ClientInfoPayloadCodec.packTypeFromNetworkId(99));
    }

    @Test
    void wireSchemaContainsNoPath() {
        assertTrue(Arrays.stream(ClientInfoPayload.class.getRecordComponents())
                .noneMatch(component -> component.getType() == Path.class));
        assertTrue(Arrays.stream(ResourcePackManifestEntry.class.getRecordComponents())
                .noneMatch(component -> component.getType() == Path.class));
    }

    private static ResourcePackManifestEntry builtIn() {
        return new ResourcePackManifestEntry("Vanilla", "", 0, ResourcePackType.BUILT_IN);
    }
}
