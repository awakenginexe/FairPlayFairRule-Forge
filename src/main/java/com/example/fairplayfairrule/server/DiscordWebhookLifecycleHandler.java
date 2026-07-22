package com.example.fairplayfairrule.server;

import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Releases bounded Discord and HTTP workers with the Forge server lifecycle. */
public final class DiscordWebhookLifecycleHandler {
    private DiscordWebhookLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerValidationService.onServerStopped();
        DiscordWebhookService.shutdown();
    }
}
