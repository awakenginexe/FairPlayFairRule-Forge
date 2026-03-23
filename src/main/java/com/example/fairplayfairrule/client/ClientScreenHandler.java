package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.FairPlayFairRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side event handler that intercepts the TitleScreen opening
 * and forces the consent screen to be shown first if the player hasn't consented yet.
 */
@OnlyIn(Dist.CLIENT)
public class ClientScreenHandler {

    /**
     * Consent flag - set to true once the player accepts the consent screen
     * This is checked before allowing access to the main menu
     */
    public static boolean hasConsented = false;

    /**
     * Listen for screen opening events and intercept TitleScreen if consent hasn't been given
     *
     * @param event The screen opening event
     */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        // Check if the screen being opened is the TitleScreen AND consent hasn't been given
        if (event.getScreen() instanceof TitleScreen && !hasConsented) {
            FairPlayFairRule.LOGGER.info("Intercepting TitleScreen - consent not yet given");

            // Cancel the TitleScreen from opening by replacing with our consent screen
            event.setCanceled(true);

            // Open the consent screen instead
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ConsentScreen());
        }
    }
}
