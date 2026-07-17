package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Title-screen consent hook for Forge 40. */
@OnlyIn(Dist.CLIENT)
public final class ClientScreenHandler {

    public static boolean hasConsented = false;

    private ClientScreenHandler() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.InitScreenEvent.Pre event) {
        if (event.getScreen() instanceof TitleScreen && !hasConsented) {
            FairPlayFairRule.LOGGER.info("Intercepting TitleScreen - consent not yet given");
            event.setCanceled(true);
            Minecraft.getInstance().setScreen(new ConsentScreen());
        }
    }
}
