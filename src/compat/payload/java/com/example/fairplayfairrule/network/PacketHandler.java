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

/** Forge 50-52 transport implementation for Minecraft 1.20.6 and 1.21.1. */
public final class PacketHandler {

    private static final int PROTOCOL_VERSION = 3;

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
        private final ClientInfoPayload payload;

        public ClientInfoPacket(ClientInfoPayload payload) {
            this.payload = payload;
        }

        public static void encode(ClientInfoPacket packet, FriendlyByteBuf buf) {
            ClientInfoPayloadCodec.encode(packet.payload, buf);
        }

        public static ClientInfoPacket decode(FriendlyByteBuf buf) {
            return new ClientInfoPacket(ClientInfoPayloadCodec.decode(buf));
        }

        public static void handle(ClientInfoPacket packet, CustomPayloadEvent.Context context) {
            ServerPlayer player = context.getSender();
            if (player != null) {
                FairPlayFairRule.LOGGER.info("Received client info from player: {}", player.getName().getString());
                ServerValidationService.validatePlayer(player, packet.payload);
            } else {
                FairPlayFairRule.LOGGER.warn("Received ClientInfoPacket but player context is null!");
            }
            context.setPacketHandled(true);
        }
    }
}
