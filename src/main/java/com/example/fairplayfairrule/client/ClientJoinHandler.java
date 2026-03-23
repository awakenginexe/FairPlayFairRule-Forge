package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side handler for player login events
 * Triggers data collection and sending when the player joins a server
 */
@OnlyIn(Dist.CLIENT)
public class ClientJoinHandler {

    /**
     * Listen for client player login event
     * When the player joins a server (and has consented), collect and send data
     *
     * @param event The client player logging in event
     */
    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Only proceed if the player has consented to data collection
        if (!ClientScreenHandler.hasConsented) {
            FairPlayFairRule.LOGGER.warn("Player joined but hasn't consented yet - skipping data collection");
            return;
        }

        // Check if connected to a server (not singleplayer)
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null || minecraft.isLocalServer()) {
            FairPlayFairRule.LOGGER.info("Player joined server - collecting and sending client data");

            // Collect and send mod/resource pack data to server
            ClientDataService.collectAndSendData();
        }
    }
}
