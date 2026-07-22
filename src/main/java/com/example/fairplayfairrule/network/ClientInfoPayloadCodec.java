package com.example.fairplayfairrule.network;

import com.example.fairplayfairrule.resourcepack.ResourcePackLimits;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import com.example.fairplayfairrule.resourcepack.ValidationFailureCode;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** One bounded wire codec shared by every Forge transport adapter. */
public final class ClientInfoPayloadCodec {
    private ClientInfoPayloadCodec() {
    }

    public static void encode(ClientInfoPayload payload, FriendlyByteBuf buffer) {
        buffer.writeByte(payload.reportType().networkId());
        buffer.writeVarInt(payload.modList().size());
        for (String mod : payload.modList()) {
            buffer.writeUtf(mod, ClientInfoPayload.MAX_MOD_ENTRY_LENGTH);
        }
        buffer.writeVarInt(payload.resourcePacks().size());
        for (ResourcePackManifestEntry pack : payload.resourcePacks()) {
            buffer.writeUtf(pack.displayName(), ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH);
            buffer.writeUtf(pack.sha256(), ResourcePackLimits.SHA256_LENGTH);
            buffer.writeLong(pack.size());
            buffer.writeByte(pack.type().ordinal());
            buffer.writeUtf(pack.identity(), ResourcePackLimits.MAX_PACK_IDENTITY_LENGTH);
        }
    }

    public static ClientInfoPayload decode(FriendlyByteBuf buffer) {
        ResourcePackReportType reportType = ResourcePackReportType.fromNetworkId(buffer.readUnsignedByte());
        int modCount = readCount(buffer, ClientInfoPayload.MAX_MOD_ENTRIES, "mod");
        List<String> mods = new ArrayList<>(modCount);
        for (int index = 0; index < modCount; index++) {
            mods.add(buffer.readUtf(ClientInfoPayload.MAX_MOD_ENTRY_LENGTH));
        }

        int packCount = readCount(buffer, ResourcePackLimits.MAX_MANIFEST_ENTRIES, "resource pack");
        List<ResourcePackManifestEntry> packs = new ArrayList<>(packCount);
        for (int index = 0; index < packCount; index++) {
            String name = buffer.readUtf(ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH);
            String sha256 = buffer.readUtf(ResourcePackLimits.SHA256_LENGTH);
            long size = buffer.readLong();
            ResourcePackType type = packTypeFromNetworkId(buffer.readUnsignedByte());
            String identity = buffer.readUtf(ResourcePackLimits.MAX_PACK_IDENTITY_LENGTH);
            packs.add(new ResourcePackManifestEntry(name, sha256, size, type, identity));
        }
        return new ClientInfoPayload(reportType, mods, packs);
    }

    /** Converts hostile bounded-wire failures into a path-free authenticated handler event. */
    public static NetworkDecodeResult decodeNetwork(FriendlyByteBuf buffer) {
        ResourcePackReportType reportType = null;
        try {
            reportType = ResourcePackReportType.fromNetworkId(buffer.readUnsignedByte());
            int modCount = readCount(buffer, ClientInfoPayload.MAX_MOD_ENTRIES, "mod");
            List<String> mods = new ArrayList<>(modCount);
            for (int index = 0; index < modCount; index++) {
                mods.add(buffer.readUtf(ClientInfoPayload.MAX_MOD_ENTRY_LENGTH));
            }
            int packCount = readCount(buffer, ResourcePackLimits.MAX_MANIFEST_ENTRIES,
                    "resource pack");
            List<ResourcePackManifestEntry> packs = new ArrayList<>(packCount);
            for (int index = 0; index < packCount; index++) {
                String name = buffer.readUtf(ResourcePackLimits.MAX_DISPLAY_NAME_LENGTH);
                String sha256 = buffer.readUtf(ResourcePackLimits.SHA256_LENGTH);
                long size = buffer.readLong();
                ResourcePackType type = packTypeFromNetworkId(buffer.readUnsignedByte());
                String identity = buffer.readUtf(ResourcePackLimits.MAX_PACK_IDENTITY_LENGTH);
                packs.add(new ResourcePackManifestEntry(name, sha256, size, type, identity));
            }
            return new NetworkDecodeResult(new ClientInfoPayload(reportType, mods, packs),
                    reportType, ValidationFailureCode.NONE);
        } catch (OversizedWireInput exception) {
            return new NetworkDecodeResult(null, reportType,
                    ValidationFailureCode.OVERSIZED_MANIFEST);
        } catch (RuntimeException exception) {
            return new NetworkDecodeResult(null, reportType,
                    ValidationFailureCode.MALFORMED_MANIFEST);
        }
    }

    public static ResourcePackType packTypeFromNetworkId(int id) {
        ResourcePackType[] values = ResourcePackType.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown resource-pack type: " + id);
        }
        return values[id];
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new OversizedWireInput();
        }
        return count;
    }

    public record NetworkDecodeResult(ClientInfoPayload payload, ResourcePackReportType reportType,
                                      ValidationFailureCode failureCode) {
        public boolean isValid() { return payload != null; }
    }

    private static final class OversizedWireInput extends IllegalArgumentException { }
}
