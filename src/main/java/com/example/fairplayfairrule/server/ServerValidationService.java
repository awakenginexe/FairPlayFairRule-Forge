package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.compat.TextComponents;
import com.example.fairplayfairrule.config.Config;
import com.example.fairplayfairrule.network.ClientInfoPayload;
import com.example.fairplayfairrule.network.ResourcePackReportType;
import com.example.fairplayfairrule.resourcepack.AuthenticatedPackSessions;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import com.example.fairplayfairrule.resourcepack.ResourcePackViolation;
import com.example.fairplayfairrule.resourcepack.ValidationFailureCode;
import com.example.fairplayfairrule.resourcepack.ValidationResult;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Authenticated server-side orchestration; policy decisions precede all reporting. */
public final class ServerValidationService {
    private static final AuthenticatedPackSessions PACK_SESSIONS = new AuthenticatedPackSessions();

    private ServerValidationService() { }

    public static void validatePlayer(ServerPlayer player, ClientInfoPayload payload) {
        UUID playerId = player.getUUID();
        Object connectionToken = player.connection;
        FairPlayFairRule.LOGGER.info("Processing {} client report for authenticated UUID {}",
                payload.reportType(), playerId);

        String bannedMod = findBannedMod(payload.modList());
        if (bannedMod != null) {
            FairPlayFairRule.LOGGER.warn("Authenticated UUID {} has a banned mod", playerId);
            player.connection.disconnect(TextComponents.literal(
                    "\u00A7c\u00A7lYou have been banned!\n\n"
                            + "\u00A77Reason: \u00A7fUsing prohibited mod: \u00A7e" + bannedMod
                            + "\n\n\u00A77This has been reported to the server administrators."));
            DiscordWebhookService.sendBanNotification(player, bannedMod, payload.modList(),
                    safePackReport(payload.resourcePacks()));
            return;
        }

        ResourcePackPolicyService policy = Config.getResourcePackPolicy();
        ValidationResult validation;
        boolean baselineEstablished = false;
        if (payload.reportType() == ResourcePackReportType.JOIN) {
            validation = policy.validateJoin(playerId, payload.resourcePacks());
            if (validation.isValid() && policy.enabled()) {
                baselineEstablished = PACK_SESSIONS.establish(
                        playerId, connectionToken, validation, policy);
                if (!baselineEstablished) {
                    validation = ValidationResult.invalid(new ResourcePackViolation(
                            ValidationFailureCode.SESSION_STATE_CHANGED,
                            "Resource pack verification failed.\n\nAnother connection already owns "
                                    + "the authenticated resource-pack session.",
                            "", "", "", List.of(),
                            "Overlapping authenticated connection.", false));
                }
            }
        } else {
            AuthenticatedPackSessions.ConnectionState session =
                    PACK_SESSIONS.connection(playerId, connectionToken);
            if (session.status() == AuthenticatedPackSessions.Status.BEFORE_BASELINE) {
                FairPlayFairRule.LOGGER.debug(
                        "Ignored runtime resource-pack report before a validated baseline for UUID {}",
                        playerId);
                return;
            }
            if (session.status() == AuthenticatedPackSessions.Status.WRONG_CONNECTION) {
                validation = ValidationResult.invalid(new ResourcePackViolation(
                        ValidationFailureCode.SESSION_STATE_CHANGED,
                        "Resource pack verification failed.\n\nThis connection does not own the "
                                + "authenticated resource-pack session.",
                        "", "", "", List.of(), "Stale connection report.", false));
            } else {
                validation = session.policy().validateRuntime(
                        session.baseline(), payload.resourcePacks());
            }
        }

        if (!validation.isValid()) {
            FairPlayFairRule.LOGGER.warn(
                    "Resource-pack integrity rejection for authenticated UUID {} during {} ({})",
                    playerId, payload.reportType(), validation.code());
            player.connection.disconnect(TextComponents.literal(validation.message()));
            DiscordWebhookService.sendResourcePackViolation(player, payload,
                    payload.reportType() == ResourcePackReportType.JOIN
                            ? ResourcePackViolationPhase.LOGIN
                            : ResourcePackViolationPhase.RUNTIME_RELOAD,
                    validation);
            return;
        }

        if (payload.reportType() == ResourcePackReportType.JOIN) {
            DiscordWebhookService.sendPlayerJoinNotification(player);
        }
        DiscordWebhookService.sendPlayerManifest(player, payload.modList(),
                safePackReport(payload.resourcePacks()), payload.resourcePacks().size(), 0);
        FairPlayFairRule.LOGGER.info(
                "Resource-pack validation complete for authenticated UUID {} (baseline established: {})",
                playerId, baselineEstablished);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        PACK_SESSIONS.clear(player.getUUID(), player.connection);
    }

    public static void onServerStopped() { PACK_SESSIONS.clearAll(); }

    private static String findBannedMod(List<String> clientMods) {
        List<? extends String> bannedModIds = Config.BANNED_MOD_IDS.get();
        for (String clientModEntry : clientMods) {
            int separator = clientModEntry.indexOf('@');
            String modId = (separator < 0 ? clientModEntry : clientModEntry.substring(0, separator))
                    .toLowerCase(Locale.ROOT);
            for (String bannedModId : bannedModIds) {
                if (modId.equalsIgnoreCase(bannedModId)) return modId;
            }
        }
        return null;
    }

    private static List<String> safePackReport(List<ResourcePackManifestEntry> manifest) {
        List<String> safe = new ArrayList<>(manifest.size());
        for (ResourcePackManifestEntry pack : manifest) {
            String hash = pack.sha256().isEmpty() ? "n/a" : pack.sha256();
            safe.add(pack.displayName().replace('\r', ' ').replace('\n', ' ')
                    + " | type=" + pack.type() + " | size=" + pack.size()
                    + " | sha256=" + hash);
        }
        return safe;
    }
}
