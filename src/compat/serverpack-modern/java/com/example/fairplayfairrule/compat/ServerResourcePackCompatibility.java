package com.example.fairplayfairrule.compat;

import net.minecraft.server.MinecraftServer;

/** Minecraft 1.19+ server-pack advertisement adapter. */
public final class ServerResourcePackCompatibility {
    private ServerResourcePackCompatibility() { }
    public static boolean isAdvertised(MinecraftServer server) {
        return server.getServerResourcePack().isPresent();
    }
}
