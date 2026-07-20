package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.compat.TextComponents;
import com.example.fairplayfairrule.config.Config;
import com.example.fairplayfairrule.network.ClientInfoPayload;
import com.example.fairplayfairrule.resourcepack.PlayerPackSessionStore;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackValidationCoordinator;
import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import com.example.fairplayfairrule.resourcepack.ValidationResult;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Server-side orchestration for existing mod checks and resource-pack integrity policy. */
public final class ServerValidationService {
    private static final PlayerPackSessionStore PACK_SESSIONS = new PlayerPackSessionStore();

    private ServerValidationService() {
    }

    public static void validatePlayer(ServerPlayer player, ClientInfoPayload payload) {
        UUID playerId = player.getUUID();
        FairPlayFairRule.LOGGER.info("Processing {} client report for player {}",
                payload.reportType(), player.getName().getString());

        ResourcePackPolicyService policy = Config.getResourcePackPolicy();
        ValidationResult structure = policy.validateManifestStructure(payload.resourcePacks());
        String bannedMod = findBannedMod(payload.modList());
        if (bannedMod != null) {
            FairPlayFairRule.LOGGER.warn("Player {} has banned mod: {}",
                    player.getName().getString(), bannedMod);
            player.connection.disconnect(TextComponents.literal(
                    "\u00A7c\u00A7lYou have been banned!\n\n" +
                    "\u00A77Reason: \u00A7fUsing prohibited mod: \u00A7e" + bannedMod + "\n\n" +
                    "\u00A77This has been reported to the server administrators."));
            List<String> reportedPacks = structure.isValid()
                    ? safePackReport(payload.resourcePacks())
                    : List.of("Resource-pack manifest rejected: " + structure.code());
            DiscordWebhookService.sendBanNotification(player, bannedMod, payload.modList(), reportedPacks);
            return;
        }

        if (!structure.isValid()) {
            FairPlayFairRule.LOGGER.warn("Malformed resource-pack report from player {}: {}",
                    player.getName().getString(), structure.code());
            player.connection.disconnect(TextComponents.literal(structure.message()));
            return;
        }

        ResourcePackValidationCoordinator.FlowResult flow =
                ResourcePackValidationCoordinator.validateReport(
                        policy, PACK_SESSIONS, playerId, payload.resourcePacks());
        ValidationResult validation = flow.validation();
        if (!validation.isValid()) {
            FairPlayFairRule.LOGGER.warn("Resource-pack validation failed for player {}: {}",
                    player.getName().getString(), validation.code());
            player.connection.disconnect(TextComponents.literal(validation.message()));
            return;
        }

        if (flow.baselineEstablished()) {
            DiscordWebhookService.sendPlayerJoinNotification(player);
        }
        List<String> safePacks = safePackReport(payload.resourcePacks());
        DiscordWebhookService.sendPlayerManifest(
                player, payload.modList(), safePacks, payload.resourcePacks().size(), 0);
        FairPlayFairRule.LOGGER.info("Resource-pack validation complete for player {} (baseline established: {})",
                player.getName().getString(), flow.baselineEstablished());
    }

    public static void onPlayerDisconnect(UUID playerId) {
        PACK_SESSIONS.clear(playerId);
    }

    private static String findBannedMod(List<String> clientMods) {
        List<? extends String> bannedModIds = Config.BANNED_MOD_IDS.get();
        for (String clientModEntry : clientMods) {
            int separator = clientModEntry.indexOf('@');
            String modId = (separator < 0 ? clientModEntry : clientModEntry.substring(0, separator))
                    .toLowerCase(Locale.ROOT);
            for (String bannedModId : bannedModIds) {
                if (modId.equalsIgnoreCase(bannedModId)) {
                    return modId;
                }
            }
        }
        return null;
    }

    private static List<String> safePackReport(List<ResourcePackManifestEntry> manifest) {
        List<String> safe = new ArrayList<>(manifest.size());
        for (ResourcePackManifestEntry pack : manifest) {
            String hash = pack.sha256().isEmpty() ? "n/a" : pack.sha256();
            safe.add(pack.displayName() + " | type=" + pack.type()
                    + " | size=" + pack.size() + " | sha256=" + hash);
        }
        return safe;
    }
}
