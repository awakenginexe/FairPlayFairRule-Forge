package com.example.fairplayfairrule.resourcepack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Connection-aware, side-effect-bounded orchestration shared by the server handler and tests. */
public final class ResourcePackReportCoordinator {
    private ResourcePackReportCoordinator() { }

    public static Outcome join(ResourcePackPolicyService policy, AuthenticatedPackSessions sessions,
                               UUID playerId, Object connectionToken,
                               List<ResourcePackManifestEntry> manifest,
                               boolean serverPackBootstrapAllowed) {
        ValidationResult validation = policy.validateJoin(playerId, manifest);
        if (!validation.isValid()) return new Outcome(validation, false, false);

        boolean stored = true;
        boolean baselineEstablished = false;
        if (policy.enabled()) {
            stored = sessions.establish(playerId, connectionToken, validation, policy,
                    serverPackBootstrapAllowed);
            baselineEstablished = stored;
        } else if (policy.requiresHashes()) {
            stored = sessions.establishMonitoring(playerId, connectionToken, policy);
        }
        if (!stored) return new Outcome(overlappingConnection(), false, false);
        return new Outcome(validation, baselineEstablished, false);
    }

    public static Outcome reload(AuthenticatedPackSessions sessions, UUID playerId,
                                 Object connectionToken,
                                 List<ResourcePackManifestEntry> manifest) {
        AuthenticatedPackSessions.ConnectionState state = sessions.connection(playerId, connectionToken);
        if (state.status() == AuthenticatedPackSessions.Status.BEFORE_BASELINE) {
            return new Outcome(null, false, true);
        }
        if (state.status() == AuthenticatedPackSessions.Status.WRONG_CONNECTION) {
            return new Outcome(staleConnection(), false, false);
        }
        if (state.baseline() == null) {
            return new Outcome(state.policy().validateJoin(playerId, manifest), false, false);
        }

        ValidationResult validation = state.policy().validateRuntime(state.baseline(), manifest);
        if (!validation.isValid() && state.serverPackBootstrapAllowed()) {
            Optional<ValidationResult> bootstrap = state.policy()
                    .validateInitialServerPack(state.baseline(), manifest);
            if (bootstrap.isPresent()) {
                validation = bootstrap.get();
                if (validation.isValid()) {
                    sessions.completeServerPackBootstrap(playerId, connectionToken, validation);
                }
            }
        }
        return new Outcome(validation, false, false);
    }

    private static ValidationResult overlappingConnection() {
        return ValidationResult.invalid(new ResourcePackViolation(
                ValidationFailureCode.SESSION_STATE_CHANGED,
                "Resource pack verification failed.\n\nAnother connection already owns "
                        + "the authenticated resource-pack session.",
                "", "", "", List.of(), "Overlapping authenticated connection.", false));
    }

    private static ValidationResult staleConnection() {
        return ValidationResult.invalid(new ResourcePackViolation(
                ValidationFailureCode.SESSION_STATE_CHANGED,
                "Resource pack verification failed.\n\nThis connection does not own the "
                        + "authenticated resource-pack session.",
                "", "", "", List.of(), "Stale connection report.", false));
    }

    public record Outcome(ValidationResult validation, boolean baselineEstablished,
                          boolean ignored) { }
}
