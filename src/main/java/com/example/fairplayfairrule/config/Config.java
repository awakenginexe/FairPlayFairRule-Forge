package com.example.fairplayfairrule.config;

import com.example.fairplayfairrule.FairPlayFairRule;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side configuration for FairPlayFairRule
 * This config is stored in the global config directory (config/fairplayfairrule-common.toml).
 */
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Discord Webhook URL for sending notifications
    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_URL;

    // List of banned mod IDs that will trigger auto-ban
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_MOD_IDS;

    private static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("General Settings");

        WEBHOOK_URL = BUILDER
                .comment("Discord Webhook URL for sending player notifications.",
                        "Leave empty to disable webhook notifications.")
                .define("webhookUrl", "");

        BANNED_MOD_IDS = BUILDER
                .comment("List of mod IDs that will trigger an automatic ban.",
                        "Example: [\"cheatmod\", \"xray\", \"killaura\"]",
                        "Mod ID matching is case-insensitive.")
                .defineList("bannedModIds",
                        new ArrayList<>(List.of("examplehackmod", "examplecheatmod")),
                        obj -> obj instanceof String);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Register the server configuration
     * Called during mod initialization
     */
    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SPEC);
        FairPlayFairRule.LOGGER.info("Registered server configuration for FairPlayFairRule");
    }
}
