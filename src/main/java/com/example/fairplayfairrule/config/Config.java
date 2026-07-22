package com.example.fairplayfairrule.config;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import com.example.fairplayfairrule.server.ResourcePackPolicyLogSummary;
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
    public static final ForgeConfigSpec.BooleanValue HASTEBIN_MIRROR_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RESOURCE_PACK_INTEGRITY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue LOG_RESOURCE_PACK_VIOLATIONS;

    // List of banned mod IDs that will trigger auto-ban
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_MOD_IDS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQUIRED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GLOBAL_APPROVED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_APPROVED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SERVER_DOWNLOADED_PACK_HASHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_PACK_HASHES;

    private static final ForgeConfigSpec SPEC;
    private static final LastKnownGoodPolicy RESOURCE_PACK_POLICY = new LastKnownGoodPolicy(
            ResourcePackPolicyService.load(false, List.of(), List.of(), List.of(), List.of(),
                    List.of()).policy());

    static {
        BUILDER.push("General Settings");

        WEBHOOK_URL = BUILDER
                .comment("Discord Webhook URL for sending player notifications.",
                        "Leave empty to disable webhook notifications.")
                .define("webhookUrl", "");

        HASTEBIN_MIRROR_ENABLED = BUILDER
                .comment("Optionally mirror manifest text to Hastebin on a best-effort basis.",
                        "Discord attachments remain authoritative and are always attempted directly.")
                .define("hastebinMirrorEnabled", false);

        RESOURCE_PACK_INTEGRITY_ENABLED = BUILDER
                .comment("Enforce resource-pack allowlists and lock policy-controlled packs for a session.",
                        "Disabled by default for backward compatibility.")
                .define("resourcePackIntegrityEnabled", false);

        LOG_RESOURCE_PACK_VIOLATIONS = BUILDER
                .comment("Send resource-pack integrity rejections to the configured Discord webhook.",
                        "Disabling this never changes enforcement decisions.")
                .define("logResourcePackViolations", true);

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

        BANNED_PACK_HASHES = BUILDER
                .comment("Raw SHA-256 hashes that are rejected before every approval list.")
                .defineList("bannedPackHashes", new ArrayList<>(), obj -> obj instanceof String);

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
        return RESOURCE_PACK_POLICY.current();
    }

    private static void refreshResourcePackPolicy() {
        ResourcePackPolicyService.PolicyLoadResult loaded = ResourcePackPolicyService.load(
                RESOURCE_PACK_INTEGRITY_ENABLED.get(),
                copy(REQUIRED_PACK_HASHES.get()),
                copy(GLOBAL_APPROVED_PACK_HASHES.get()),
                copy(PLAYER_APPROVED_PACK_HASHES.get()),
                copy(SERVER_DOWNLOADED_PACK_HASHES.get()),
                copy(BANNED_PACK_HASHES.get()));
        for (String error : loaded.errors()) {
            FairPlayFairRule.LOGGER.warn("Invalid resource-pack policy configuration: {}", error);
        }
        if (!RESOURCE_PACK_POLICY.accept(loaded)) {
            FairPlayFairRule.LOGGER.warn("Resource-pack policy reload rejected; retaining the last-known-good policy.");
            return;
        }
        ResourcePackPolicyLogSummary summary = ResourcePackPolicyLogSummary.from(
                loaded.policy(), loaded.errors().size());
        FairPlayFairRule.LOGGER.info(summary.message());
        if (summary.inactiveApprovalWarningRequired()) {
            FairPlayFairRule.LOGGER.warn(summary.inactiveApprovalWarning());
        }
    }

    private static List<String> copy(List<? extends String> values) {
        return new ArrayList<>(values);
    }
}
