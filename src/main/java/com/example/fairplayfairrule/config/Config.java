package com.example.fairplayfairrule.config;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

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

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQUIRED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GLOBAL_APPROVED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_APPROVED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SERVER_DOWNLOADED_PACK_HASHES;

    private static final ForgeConfigSpec SPEC;
    private static volatile ResourcePackPolicyService resourcePackPolicy = ResourcePackPolicyService.load(
            List.of(), List.of(), List.of(), List.of()).policy();

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

        REQUIRED_PACK_HASHES = BUILDER
                .comment("Raw SHA-256 hashes of ZIP resource packs every player must have active.",
                        "Hashes are normalized case-insensitively and must contain 64 hexadecimal characters.")
                .defineList("requiredPackHashes", new ArrayList<>(), obj -> obj instanceof String);

        GLOBAL_APPROVED_PACK_HASHES = BUILDER
                .comment("Raw SHA-256 hashes of optional ZIP resource packs allowed for every player.")
                .defineList("globalApprovedPackHashes", new ArrayList<>(), obj -> obj instanceof String);

        PLAYER_APPROVED_PACK_HASHES = BUILDER
                .comment("Per-player optional approvals in UUID=SHA256 form.",
                        "Repeat a UUID with different hashes to approve multiple packs for that player.")
                .defineList("playerApprovedPackHashes", new ArrayList<>(), obj -> obj instanceof String);

        SERVER_DOWNLOADED_PACK_HASHES = BUILDER
                .comment("Expected raw SHA-256 hashes for packs downloaded from this server.",
                        "Downloaded packs are validated separately from user-provided optional packs.")
                .defineList("serverDownloadedPackHashes", new ArrayList<>(), obj -> obj instanceof String);

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

    public static void onConfigEvent(ModConfigEvent event) {
        if (FairPlayFairRule.MOD_ID.equals(event.getConfig().getModId())) {
            refreshResourcePackPolicy();
        }
    }

    public static ResourcePackPolicyService getResourcePackPolicy() {
        return resourcePackPolicy;
    }

    private static void refreshResourcePackPolicy() {
        ResourcePackPolicyService.PolicyLoadResult loaded = ResourcePackPolicyService.load(
                copy(REQUIRED_PACK_HASHES.get()),
                copy(GLOBAL_APPROVED_PACK_HASHES.get()),
                copy(PLAYER_APPROVED_PACK_HASHES.get()),
                copy(SERVER_DOWNLOADED_PACK_HASHES.get()));
        resourcePackPolicy = loaded.policy();
        for (String error : loaded.errors()) {
            FairPlayFairRule.LOGGER.error("Invalid resource-pack policy configuration: {}", error);
        }
        FairPlayFairRule.LOGGER.info("Loaded resource-pack integrity policy with {} configuration error(s)",
                loaded.errors().size());
    }

    private static List<String> copy(List<? extends String> values) {
        return new ArrayList<>(values);
    }
}
