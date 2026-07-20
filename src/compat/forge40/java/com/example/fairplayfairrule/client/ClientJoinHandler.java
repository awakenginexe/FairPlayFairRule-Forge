package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import net.minecraft.client.Minecraft;
import com.example.fairplayfairrule.network.ResourcePackReportType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client join hook for Forge 40. */
@OnlyIn(Dist.CLIENT)
public final class ClientJoinHandler {

    private ClientJoinHandler() {
    }

    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggedInEvent event) {
        if (!ClientScreenHandler.hasConsented) {
            FairPlayFairRule.LOGGER.warn("Player joined but hasn't consented yet - skipping data collection");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null || minecraft.isLocalServer()) {
            FairPlayFairRule.LOGGER.info("Player joined server - collecting and sending client data");
            ClientDataService.collectAndSendData(ResourcePackReportType.JOIN);
        }
    }
}
