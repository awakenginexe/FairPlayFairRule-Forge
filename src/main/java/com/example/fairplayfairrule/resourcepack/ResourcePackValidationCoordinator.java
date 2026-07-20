package com.example.fairplayfairrule.resourcepack;

import java.util.List;
import java.util.UUID;

/** Atomically applies join policy once, then locks every later report to that baseline. */
public final class ResourcePackValidationCoordinator {
    private ResourcePackValidationCoordinator() {
    }

    public static FlowResult validateReport(ResourcePackPolicyService policy,
                                            PlayerPackSessionStore sessions,
                                            UUID playerId,
                                            List<ResourcePackManifestEntry> manifest) {
        synchronized (sessions) {
            PlayerPackSessionStore.SessionBaseline existing = sessions.get(playerId).orElse(null);
            if (existing != null) {
                return new FlowResult(policy.validateRuntime(existing, manifest), false);
            }

            ValidationResult initial = policy.validateJoin(playerId, manifest);
            if (initial.isValid()) {
                sessions.storeValidated(playerId, initial);
                return new FlowResult(initial, true);
            }
            return new FlowResult(initial, false);
        }
    }

    public record FlowResult(ValidationResult validation, boolean baselineEstablished) {
    }
}
