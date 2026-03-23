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
 * Packet handler for registering and handling network packets
 * Uses Forge 1.20.1 SimpleChannel networking
 */
public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FairPlayFairRule.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * Register all network packets
     * Called during FMLCommonSetupEvent
     */
    public static void register() {
        int id = 0;

        // Register client-to-server packet
        CHANNEL.messageBuilder(ClientInfoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientInfoPacket::encode)
                .decoder(ClientInfoPacket::decode)
                .consumerMainThread(ClientInfoPacket::handle)
                .add();

        FairPlayFairRule.LOGGER.info("Registered network packets for FairPlayFairRule");
    }

    /**
     * Client-to-Server packet containing the client's mod list and resource packs
     */
    public static class ClientInfoPacket {
        private final List<String> modList;
        private final List<String> resourcePackList;

        public ClientInfoPacket(List<String> modList, List<String> resourcePackList) {
            this.modList = modList;
            this.resourcePackList = resourcePackList;
        }

        /**
         * Encode the packet data to the network buffer
         */
        public static void encode(ClientInfoPacket packet, FriendlyByteBuf buf) {
            // Write mod list
            buf.writeInt(packet.modList.size());
            for (String mod : packet.modList) {
                buf.writeUtf(mod);
            }

            // Write resource pack list
            buf.writeInt(packet.resourcePackList.size());
            for (String pack : packet.resourcePackList) {
                buf.writeUtf(pack);
            }
        }

        /**
         * Decode the packet data from the network buffer
         */
        public static ClientInfoPacket decode(FriendlyByteBuf buf) {
            // Read mod list
            int modCount = buf.readInt();
            List<String> modList = new ArrayList<>();
            for (int i = 0; i < modCount; i++) {
                modList.add(buf.readUtf());
            }

            // Read resource pack list
            int packCount = buf.readInt();
            List<String> resourcePackList = new ArrayList<>();
            for (int i = 0; i < packCount; i++) {
                resourcePackList.add(buf.readUtf());
            }

            return new ClientInfoPacket(modList, resourcePackList);
        }

        /**
         * Handle the packet on the server side
         * This runs on the main server thread (consumerMainThread)
         */
        public static void handle(ClientInfoPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ServerPlayer player = ctx.getSender();

            if (player != null) {
                FairPlayFairRule.LOGGER.info("Received client info from player: {}", player.getName().getString());

                // Pass to validation service for processing
                ServerValidationService.validatePlayer(
                        player,
                        packet.modList,
                        packet.resourcePackList,
                        false
                );
            } else {
                FairPlayFairRule.LOGGER.warn("Received ClientInfoPacket but player context is null!");
            }

            ctx.setPacketHandled(true);
        }

        public List<String> getModList() {
            return modList;
        }

        public List<String> getResourcePackList() {
            return resourcePackList;
        }
    }
}
