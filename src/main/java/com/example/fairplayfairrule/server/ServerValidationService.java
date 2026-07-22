package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.compat.TextComponents;
import com.example.fairplayfairrule.compat.ServerResourcePackCompatibility;
import com.example.fairplayfairrule.config.Config;
import com.example.fairplayfairrule.network.ClientInfoPayload;
import com.example.fairplayfairrule.network.ResourcePackReportType;
import com.example.fairplayfairrule.resourcepack.AuthenticatedPackSessions;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;
import com.example.fairplayfairrule.resourcepack.ResourcePackReportCoordinator;
import com.example.fairplayfairrule.resourcepack.ResourcePackViolation;
import com.example.fairplayfairrule.resourcepack.ResourcePackType;
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
        boolean baselineEstablished;
        if (payload.reportType() == ResourcePackReportType.JOIN) {
            boolean alreadyHasServerPack = payload.resourcePacks().stream()
                    .anyMatch(entry -> entry.type() == ResourcePackType.SERVER_DOWNLOADED);
            boolean bootstrap = policy.enabled() && policy.hasServerDownloadedApprovals()
                    && !alreadyHasServerPack && serverPackAdvertised(player);
            ResourcePackReportCoordinator.Outcome outcome = ResourcePackReportCoordinator.join(
                    policy, PACK_SESSIONS, playerId, connectionToken,
                    payload.resourcePacks(), bootstrap);
            validation = outcome.validation();
            baselineEstablished = outcome.baselineEstablished();
        } else {
            ResourcePackReportCoordinator.Outcome outcome = ResourcePackReportCoordinator.reload(
                    PACK_SESSIONS, playerId, connectionToken, payload.resourcePacks());
            if (outcome.ignored()) {
                FairPlayFairRule.LOGGER.debug(
                        "Ignored runtime resource-pack report before a validated baseline for UUID {}",
                        playerId);
                return;
            }
            validation = outcome.validation();
            baselineEstablished = false;
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

    /** Handles bounded decoder failures without retaining or echoing untrusted wire bytes. */
    public static void rejectMalformed(ServerPlayer player, ResourcePackReportType reportType,
                                       ValidationFailureCode code) {
        ResourcePackReportType phaseType = reportType == null ? ResourcePackReportType.JOIN : reportType;
        if (phaseType == ResourcePackReportType.RELOAD
                && PACK_SESSIONS.connection(player.getUUID(), player.connection).status()
                == AuthenticatedPackSessions.Status.BEFORE_BASELINE) {
            FairPlayFairRule.LOGGER.debug(
                    "Ignored malformed runtime resource-pack report before baseline for UUID {}",
                    player.getUUID());
            return;
        }
        String detail = code == ValidationFailureCode.OVERSIZED_MANIFEST
                ? "The client report exceeded a bounded entry limit."
                : "The client report could not be decoded safely.";
        ValidationResult validation = ValidationResult.invalid(new ResourcePackViolation(code,
                "Resource pack verification failed.\n\n" + detail,
                "", "", "", List.of(), detail, false));
        FairPlayFairRule.LOGGER.warn(
                "Resource-pack integrity rejection for authenticated UUID {} during {} ({})",
                player.getUUID(), phaseType, code);
        player.connection.disconnect(TextComponents.literal(validation.message()));
        DiscordWebhookService.sendResourcePackViolation(player, null,
                phaseType == ResourcePackReportType.JOIN
                        ? ResourcePackViolationPhase.LOGIN
                        : ResourcePackViolationPhase.RUNTIME_RELOAD,
                validation);
    }

    private static boolean serverPackAdvertised(ServerPlayer player) {
        try {
            return ServerResourcePackCompatibility.isAdvertised(player.getServer());
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

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
