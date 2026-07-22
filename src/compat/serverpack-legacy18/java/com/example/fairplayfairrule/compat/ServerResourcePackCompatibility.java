package com.example.fairplayfairrule.compat;

import net.minecraft.server.MinecraftServer;

/** Minecraft 1.18 server-pack advertisement adapter. */
public final class ServerResourcePackCompatibility {
    private ServerResourcePackCompatibility() { }
    public static boolean isAdvertised(MinecraftServer server) {
        String resourcePack = server.getResourcePack();
        return resourcePack != null && !resourcePack.isBlank();
    }
}
