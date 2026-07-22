package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.resourcepack.ResourcePackLimits;
import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackViolation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable sanitized handoff created only after enforcement has rejected a report. */
public record ResourcePackViolationEvent(String playerName, UUID authenticatedPlayerId,
        String minecraftVersion, String forgeVersion, String fpfrVersion, int protocolVersion,
        Instant timestamp, ResourcePackViolationPhase phase, ResourcePackViolation violation,
        List<String> normalizedMods, List<ResourcePackManifestEntry> normalizedPacks) {
    public ResourcePackViolationEvent {
        playerName = safe(playerName, 80);
        minecraftVersion = safe(minecraftVersion, 64);
        forgeVersion = safe(forgeVersion, 96);
        fpfrVersion = safe(fpfrVersion, 96);
        authenticatedPlayerId = Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        phase = Objects.requireNonNull(phase, "phase");
        violation = Objects.requireNonNull(violation, "violation");
        normalizedMods = normalizedMods == null ? List.of() : normalizedMods.stream()
                .limit(2_048).map(value -> safe(value, 256)).toList();
        normalizedPacks = normalizedPacks == null ? List.of() : normalizedPacks.stream()
                .limit(ResourcePackLimits.MAX_MANIFEST_ENTRIES).toList();
    }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String sanitized = value.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("[\\p{Cntrl}]", " ");
        return sanitized.length() <= maximum ? sanitized : sanitized.substring(0, maximum);
    }
}
