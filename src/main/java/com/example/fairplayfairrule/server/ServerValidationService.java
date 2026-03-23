package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.config.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side service for validating player mod lists and resource packs
 * Handles auto-ban logic and Discord webhook notifications
 */
public class ServerValidationService {

    // Track whether each player has already sent their initial join data
    private static final Map<UUID, Boolean> playerInitialJoinMap = new HashMap<>();

    /**
     * Validate a player's mod list and resource packs
     *
     * @param player The player to validate
     * @param clientMods The list of mods from the client (format: "modId@version")
     * @param clientPacks The list of active resource packs from the client
     * @param isResourcePackUpdate Whether this is a resource pack update (vs initial join)
     */
    public static void validatePlayer(ServerPlayer player, List<String> clientMods, List<String> clientPacks, boolean isResourcePackUpdate) {
        UUID playerUUID = player.getUUID();

        // Determine if this is the first time we're receiving data from this player
        boolean isInitialJoin = !playerInitialJoinMap.containsKey(playerUUID);

        if (isInitialJoin) {
            playerInitialJoinMap.put(playerUUID, true);
            FairPlayFairRule.LOGGER.info("Processing INITIAL join data for player: {}", player.getName().getString());
        } else {
            FairPlayFairRule.LOGGER.info("Processing RESOURCE PACK UPDATE for player: {}", player.getName().getString());
        }

        // Step 1: Check for banned mods
        List<? extends String> bannedModIds = Config.BANNED_MOD_IDS.get();

        for (String clientModEntry : clientMods) {
            // Extract mod ID from "modId@version" format
            String modId = clientModEntry.split("@")[0].toLowerCase();

            // Check if this mod ID is in the banned list (case-insensitive)
            for (String bannedModId : bannedModIds) {
                if (modId.equalsIgnoreCase(bannedModId)) {
                    FairPlayFairRule.LOGGER.warn("Player {} has banned mod: {}", player.getName().getString(), modId);

                    // Kick the player immediately
                    player.connection.disconnect(Component.literal(
                            "\u00A7c\u00A7lYou have been banned!\n\n" +
                            "\u00A77Reason: \u00A7fUsing prohibited mod: \u00A7e" + modId + "\n\n" +
                            "\u00A77This has been reported to the server administrators."
                    ));

                    // Send high-priority Discord alert
                    DiscordWebhookService.sendBanNotification(player, modId, clientMods, clientPacks);

                    // Don't proceed with normal webhook sending
                    return;
                }
            }
        }

        // Step 2: Player is not banned - send webhook notifications
        int packEnabledCount = clientPacks.size();
        int packDisabledCount = 0; // We only get enabled packs from the client

        if (isInitialJoin) {
            // Initial join: Send both join notification AND player manifest
            DiscordWebhookService.sendPlayerJoinNotification(player);
            DiscordWebhookService.sendPlayerManifest(player, clientMods, clientPacks, packEnabledCount, packDisabledCount);
        } else {
            // Resource pack update: Send ONLY player manifest
            DiscordWebhookService.sendPlayerManifest(player, clientMods, clientPacks, packEnabledCount, packDisabledCount);
        }

        FairPlayFairRule.LOGGER.info("Validation complete for player: {}", player.getName().getString());
    }

    /**
     * Clear tracking data when a player disconnects
     * Should be called from a player disconnect event if needed
     */
    public static void onPlayerDisconnect(UUID playerUUID) {
        playerInitialJoinMap.remove(playerUUID);
    }
}
