package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.network.PacketHandler;
import net.minecraft.client.Minecraft;

/** Client-only sender for Forge 48-49's Channel API. */
public final class ClientPacketSender {

    private ClientPacketSender() {
    }

    public static void sendToServer(PacketHandler.ClientInfoPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            FairPlayFairRule.LOGGER.warn("Cannot send client data before the network connection is ready");
            return;
        }

        PacketHandler.CHANNEL.send(packet, minecraft.getConnection().getConnection());
    }
}
