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

import java.util.function.Supplier;

/** Forge 40 transport implementation for Minecraft 1.18.2. */
public final class PacketHandler {

    private static final String PROTOCOL_VERSION = "3";

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
                .consumer(ClientInfoPacket::handle)
                .add();
        FairPlayFairRule.LOGGER.info("Registered Forge 40 network packets");
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

        public static void handle(ClientInfoPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                context.enqueueWork(() -> {
                    FairPlayFairRule.LOGGER.info("Received client info from player: {}",
                            player.getName().getString());
                    if (packet.decoded != null && !packet.decoded.isValid()) {
                        ServerValidationService.rejectMalformed(player, packet.decoded.reportType(),
                                packet.decoded.failureCode());
                    } else {
                        ServerValidationService.validatePlayer(player, packet.payload);
                    }
                });
            } else {
                FairPlayFairRule.LOGGER.warn("Received ClientInfoPacket but player context is null!");
            }
            context.setPacketHandled(true);
        }
    }
}
