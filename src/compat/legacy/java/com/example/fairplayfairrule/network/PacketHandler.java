package com.example.fairplayfairrule.network;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.server.ServerValidationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Forge 40-47 transport implementation (Minecraft 1.18.2 through 1.20.1).
 */
public final class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_MOD_ENTRIES = 2_048;
    private static final int MAX_RESOURCE_PACK_ENTRIES = 512;
    private static final int MAX_ENTRY_LENGTH = 256;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FairPlayFairRule.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private PacketHandler() {
    }

    public static void register() {
        CHANNEL.messageBuilder(ClientInfoPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientInfoPacket::encode)
                .decoder(ClientInfoPacket::decode)
                .consumerMainThread(ClientInfoPacket::handle)
                .add();

        FairPlayFairRule.LOGGER.info("Registered legacy Forge network packets");
    }

    public static final class ClientInfoPacket {
        private final List<String> modList;
        private final List<String> resourcePackList;

        public ClientInfoPacket(List<String> modList, List<String> resourcePackList) {
            this.modList = modList;
            this.resourcePackList = resourcePackList;
        }

        public static void encode(ClientInfoPacket packet, FriendlyByteBuf buf) {
            writeEntries(buf, packet.modList, MAX_MOD_ENTRIES, "mod");
            writeEntries(buf, packet.resourcePackList, MAX_RESOURCE_PACK_ENTRIES, "resource pack");
        }

        public static ClientInfoPacket decode(FriendlyByteBuf buf) {
            return new ClientInfoPacket(
                    readEntries(buf, MAX_MOD_ENTRIES, "mod"),
                    readEntries(buf, MAX_RESOURCE_PACK_ENTRIES, "resource pack")
            );
        }

        private static void writeEntries(FriendlyByteBuf buf, List<String> entries, int maximum, String type) {
            if (entries.size() > maximum) {
                throw new IllegalArgumentException("Too many " + type + " entries: " + entries.size());
            }

            buf.writeInt(entries.size());
            for (String entry : entries) {
                buf.writeUtf(entry, MAX_ENTRY_LENGTH);
            }
        }

        private static List<String> readEntries(FriendlyByteBuf buf, int maximum, String type) {
            int count = buf.readInt();
            if (count < 0 || count > maximum) {
                throw new IllegalArgumentException("Invalid " + type + " entry count: " + count);
            }

            List<String> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(buf.readUtf(MAX_ENTRY_LENGTH));
            }
            return entries;
        }

        public static void handle(ClientInfoPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();

            if (player != null) {
                FairPlayFairRule.LOGGER.info("Received client info from player: {}", player.getName().getString());
                ServerValidationService.validatePlayer(player, packet.modList, packet.resourcePackList, false);
            } else {
                FairPlayFairRule.LOGGER.warn("Received ClientInfoPacket but player context is null!");
            }

            context.setPacketHandled(true);
        }
    }
}
