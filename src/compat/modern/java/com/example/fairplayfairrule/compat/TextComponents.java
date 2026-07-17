package com.example.fairplayfairrule.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Text component factory for Minecraft 1.19 and later. */
public final class TextComponents {

    private TextComponents() {
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }
}
