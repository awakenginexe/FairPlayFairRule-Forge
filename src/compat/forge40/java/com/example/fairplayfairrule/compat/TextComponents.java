package com.example.fairplayfairrule.compat;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;

/** Text component factory for Minecraft 1.18.2. */
public final class TextComponents {

    private TextComponents() {
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }
}
