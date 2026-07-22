package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.resourcepack.ResourcePackManifestEntry;
import com.example.fairplayfairrule.resourcepack.ResourcePackViolation;

import java.nio.charset.StandardCharsets;

/** Deterministic normalized UTF-8 evidence; raw packet bytes and local paths never enter it. */
public final class ResourcePackViolationEvidenceBuilder {
    private ResourcePackViolationEvidenceBuilder() { }

    public static byte[] build(ResourcePackViolationEvent event) {
        StringBuilder text = new StringBuilder();
        ResourcePackViolation violation = event.violation();
        text.append("=== RESOURCE PACK INTEGRITY VIOLATION ===\n")
                .append("Player: ").append(event.playerName()).append('\n')
                .append("Authenticated UUID: ").append(event.authenticatedPlayerId()).append('\n')
                .append("Phase: ").append(event.phase()).append('\n')
                .append("Violation: ").append(violation.code()).append('\n')
                .append("Reason: ").append(singleLine(violation.reason())).append('\n')
                .append("Minecraft: ").append(event.minecraftVersion()).append('\n')
                .append("Forge: ").append(event.forgeVersion()).append('\n')
                .append("FPFR / protocol: ").append(event.fpfrVersion()).append(" / ")
                .append(event.protocolVersion()).append('\n')
                .append("UTC timestamp: ").append(event.timestamp()).append('\n');
        appendIf(text, "Detected pack", violation.detectedPack());
        appendIf(text, "Expected SHA-256", violation.expectedHash());
        appendIf(text, "Received SHA-256", violation.receivedHash());
        appendIf(text, "Policy comparison", violation.comparisonDetail());
        if (!violation.missingHashes().isEmpty()) {
            text.append("Missing required SHA-256 hashes:\n");
            violation.missingHashes().forEach(hash -> text.append("- ").append(hash).append('\n'));
        }
        text.append("\n=== MODS (").append(event.normalizedMods().size()).append(") ===\n");
        event.normalizedMods().forEach(mod -> text.append(mod).append('\n'));
        text.append("\n=== ORDERED ACTIVE RESOURCE PACKS (")
                .append(event.normalizedPacks().size()).append(") ===\n");
        int order = 0;
        for (ResourcePackManifestEntry pack : event.normalizedPacks()) {
            text.append("Order: ").append(order++).append('\n')
                    .append("Name: ").append(singleLine(pack.displayName())).append('\n')
                    .append("Type: ").append(pack.type()).append('\n')
                    .append("Size: ").append(pack.size()).append(" bytes\n")
                    .append("SHA-256: ").append(pack.sha256()).append('\n');
            appendIf(text, "Trusted identity", pack.identity());
            text.append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendIf(StringBuilder text, String name, String value) {
        if (value != null && !value.isBlank()) text.append(name).append(": ").append(value).append('\n');
    }
    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }
}
