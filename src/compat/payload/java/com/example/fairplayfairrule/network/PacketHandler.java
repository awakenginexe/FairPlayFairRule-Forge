package com.example.fairplayfairrule.network;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.server.ServerValidationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

/** Forge 50-52 transport implementation for Minecraft 1.20.6 and 1.21.1. */
public final class PacketHandler {

    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_MOD_ENTRIES = 2_048;
    private static final int MAX_RESOURCE_PACK_ENTRIES = 512;
    private static final int MAX_ENTRY_LENGTH = 256;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(FairPlayFairRule.MOD_ID, "main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .simpleChannel();

    private PacketHandler() {
    }

    public static void register() {
        CHANNEL.messageBuilder(ClientInfoPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientInfoPacket::encode)
                .decoder(ClientInfoPacket::decode)
                .consumerMainThread(ClientInfoPacket::handle)
                .add();
        CHANNEL.build();

        FairPlayFairRule.LOGGER.info("Registered Forge payload network packets");
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

        public static void handle(ClientInfoPacket packet, CustomPayloadEvent.Context context) {
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
