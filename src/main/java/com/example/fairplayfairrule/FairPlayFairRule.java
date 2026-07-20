package com.example.fairplayfairrule;

import com.example.fairplayfairrule.client.ClientJoinHandler;
import com.example.fairplayfairrule.client.ClientReloadHandler;
import com.example.fairplayfairrule.client.ClientScreenHandler;
import com.example.fairplayfairrule.config.Config;
import com.example.fairplayfairrule.network.PacketHandler;
import com.example.fairplayfairrule.server.ServerPlayerLifecycleHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for FairPlayFairRule
 * A client-side verification tool that must be installed on both client and server.
 */
@Mod("fairplayfairrule")
public class FairPlayFairRule {
    public static final String MOD_ID = "fairplayfairrule";
    public static final Logger LOGGER = LoggerFactory.getLogger(FairPlayFairRule.class);

    /**
     * Mod constructor - called during mod initialization
     */
    public FairPlayFairRule() {
        LOGGER.info("Initializing FairPlayFairRule mod...");

        // Register configuration
        Config.register(ModLoadingContext.get());

        // Register common setup event for network registration
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Config::onConfigEvent);

        // Clear validated pack baselines as soon as the corresponding session ends.
        MinecraftForge.EVENT_BUS.register(ServerPlayerLifecycleHandler.class);

        // Register client-side listeners only on client distribution
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Register client reload listener (mod event bus)
            FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientReloadHandler::onRegisterClientReloadListeners);

            // Register runtime client event handlers (Forge event bus)
            MinecraftForge.EVENT_BUS.register(ClientScreenHandler.class);
            MinecraftForge.EVENT_BUS.register(ClientJoinHandler.class);
        }

        LOGGER.info("FairPlayFairRule mod initialized successfully!");
    }

    /**
     * Common setup phase - register network packets
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }
}
