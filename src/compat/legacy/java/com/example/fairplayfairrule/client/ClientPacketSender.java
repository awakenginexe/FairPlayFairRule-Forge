package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.network.PacketHandler;

/** Client-only sender for Forge 40-47's SimpleChannel API. */
public final class ClientPacketSender {

    private ClientPacketSender() {
    }

    public static void sendToServer(PacketHandler.ClientInfoPacket packet) {
        PacketHandler.CHANNEL.sendToServer(packet);
    }
}
