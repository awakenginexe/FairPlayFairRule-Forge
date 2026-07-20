package com.example.fairplayfairrule.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge 43+ logout API adapter. */
public final class ServerPlayerLifecycleHandler {
    private ServerPlayerLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerValidationService.onPlayerDisconnect(player.getUUID());
        }
    }
}
