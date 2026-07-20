package com.example.fairplayfairrule.compat;

import net.minecraft.commands.CommandSourceStack;

/** Minecraft 1.20.1+ command feedback adapter. */
public final class CommandFeedback {
    private CommandFeedback() {
    }

    public static void success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> TextComponents.literal(message), false);
    }
}
