package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;

import java.util.Objects;

/** Bounded effective policy state for startup and successful-reload logging. */
public record ResourcePackPolicyLogSummary(boolean enabled, int required, int global,
        int perPlayer, int serverDownloaded, int banned, int errors) {
    private static final String WARNING = "Resource-pack integrity is disabled; required, global, "
            + "per-player, and server-downloaded approval lists are inactive.";

    public static ResourcePackPolicyLogSummary from(ResourcePackPolicyService policy, int errors) {
        Objects.requireNonNull(policy, "policy");
        return new ResourcePackPolicyLogSummary(policy.enabled(), policy.requiredCount(),
                policy.globalApprovedCount(), policy.perPlayerApprovedCount(),
                policy.serverDownloadedCount(), policy.bannedCount(), Math.max(errors, 0));
    }

    public String message() {
        return "Resource-pack integrity policy loaded: enabled=" + enabled
                + ", required=" + required + ", global=" + global + ", per-player=" + perPlayer
                + ", server-downloaded=" + serverDownloaded + ", banned=" + banned
                + ", errors=" + errors;
    }

    public boolean inactiveApprovalWarningRequired() {
        return !enabled && (required > 0 || global > 0 || perPlayer > 0 || serverDownloaded > 0);
    }

    public String inactiveApprovalWarning() { return WARNING; }
}
