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

/**
 * Forge 48-49 transport implementation (Minecraft 1.20.2 and 1.20.4).
 */
public final class PacketHandler {

    private static final int PROTOCOL_VERSION = 3;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(new ResourceLocation(FairPlayFairRule.MOD_ID, "main"))
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

        FairPlayFairRule.LOGGER.info("Registered Forge 48+ network packets");
    }

    public static final class ClientInfoPacket {
        private final ClientInfoPayload payload;
        private final ClientInfoPayloadCodec.NetworkDecodeResult decoded;

        public ClientInfoPacket(ClientInfoPayload payload) {
            this.payload = payload;
            this.decoded = null;
        }

        private ClientInfoPacket(ClientInfoPayloadCodec.NetworkDecodeResult decoded) {
            this.payload = decoded.payload();
            this.decoded = decoded;
        }

        public static void encode(ClientInfoPacket packet, FriendlyByteBuf buf) {
            ClientInfoPayloadCodec.encode(packet.payload, buf);
        }

        public static ClientInfoPacket decode(FriendlyByteBuf buf) {
            return new ClientInfoPacket(ClientInfoPayloadCodec.decodeNetwork(buf));
        }

        public static void handle(ClientInfoPacket packet, CustomPayloadEvent.Context context) {
            ServerPlayer player = context.getSender();

            if (player != null) {
                FairPlayFairRule.LOGGER.info("Received client info from player: {}", player.getName().getString());
                if (packet.decoded != null && !packet.decoded.isValid()) {
                    ServerValidationService.rejectMalformed(player, packet.decoded.reportType(),
                            packet.decoded.failureCode());
                } else {
                    ServerValidationService.validatePlayer(player, packet.payload);
                }
            } else {
                FairPlayFairRule.LOGGER.warn("Received ClientInfoPacket but player context is null!");
            }

            context.setPacketHandled(true);
        }
    }
}
