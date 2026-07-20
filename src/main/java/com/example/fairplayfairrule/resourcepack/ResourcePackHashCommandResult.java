package com.example.fairplayfairrule.resourcepack;

import java.util.Objects;
import java.util.UUID;

/** Copy-ready, path-free output from an administrator pack-hash request. */
public record ResourcePackHashCommandResult(
        String fileName,
        long fileSize,
        String sha256,
        UUID playerId) {

    public ResourcePackHashCommandResult {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(sha256, "sha256");
    }

    public String formattedOutput() {
        StringBuilder output = new StringBuilder()
                .append("File: ").append(fileName).append('\n')
                .append("Size: ").append(fileSize).append(" bytes\n")
                .append("SHA-256: ").append(sha256).append('\n')
                .append("Hash-list entry: \"").append(sha256).append('"');
        if (playerId != null) {
            output.append('\n')
                    .append("Per-player entry: \"")
                    .append(playerId).append('=').append(sha256).append('"');
        }
        return output.toString();
    }
}
