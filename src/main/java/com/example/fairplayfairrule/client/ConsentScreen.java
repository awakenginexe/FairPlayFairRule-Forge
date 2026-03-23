package com.example.fairplayfairrule.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Mandatory consent screen that blocks access to the game until the player accepts or declines.
 * Uses Minecraft's built-in ConfirmScreen for proper text rendering without blur.
 * This screen explains that the player's mod list and resource packs will be collected
 * and sent to the server administrator for anti-cheat purposes.
 */
@OnlyIn(Dist.CLIENT)
public class ConsentScreen extends ConfirmScreen {

    private static final Component TITLE = Component.literal("FairPlayFairRule - User Consent Required");

    private static final Component MESSAGE = Component.literal(
            "This server requires the FairPlayFairRule mod for anti-cheat purposes.\n\n" +
            "By accepting, you acknowledge that:\n" +
            "• Your complete mod list will be collected and sent to the server administrator\n" +
            "• Your active resource packs will be collected and sent to the server administrator\n" +
            "• This information may be logged and reviewed for anti-cheat purposes\n" +
            "• Resource pack changes during gameplay will be re-reported\n\n" +
            "If you decline, you will not be able to play on this server."
    );

    public ConsentScreen() {
        super(
                ConsentScreen::onConfirm, // Accept callback
                TITLE,
                MESSAGE,
                Component.literal("Accept & Continue").withStyle(style -> style.withColor(0x00FF00)), // Green
                Component.literal("Decline & Quit").withStyle(style -> style.withColor(0xFF0000))  // Red
        );
    }

    /**
     * Called when the "Accept & Continue" button is clicked
     * Sets the consent flag to true and opens the main menu
     */
    private static void onConfirm(boolean accepted) {
        Minecraft minecraft = Minecraft.getInstance();

        if (accepted) {
            // User accepted - set consent flag and proceed to main menu
            ClientScreenHandler.hasConsented = true;
            minecraft.setScreen(new TitleScreen());
        } else {
            // User declined - quit the game
            minecraft.stop();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Prevent closing with ESC key - user must make a choice
        return false;
    }
}
