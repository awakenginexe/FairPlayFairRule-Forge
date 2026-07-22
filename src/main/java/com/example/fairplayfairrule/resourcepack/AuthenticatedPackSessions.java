package com.example.fairplayfairrule.resourcepack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Binds each ordered baseline and policy snapshot to one authenticated play connection. */
public final class AuthenticatedPackSessions {
    private final Map<UUID, Active> active = new HashMap<>();

    public synchronized boolean establish(UUID playerId, Object connectionToken,
                                          ValidationResult validation,
                                          ResourcePackPolicyService policy) {
        return establish(playerId, connectionToken, validation, policy, false);
    }

    public synchronized boolean establish(UUID playerId, Object connectionToken,
                                          ValidationResult validation,
                                          ResourcePackPolicyService policy,
                                          boolean serverPackBootstrapAllowed) {
        if (playerId == null || connectionToken == null || validation == null || policy == null
                || !validation.isValid() || !policy.enabled() || active.containsKey(playerId)) {
            return false;
        }
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(playerId, validation);
        active.put(playerId, new Active(connectionToken,
                store.get(playerId).orElseThrow(), policy, serverPackBootstrapAllowed));
        return true;
    }

    /** Binds a disabled-integrity connection only so configured bans remain enforceable. */
    public synchronized boolean establishMonitoring(UUID playerId, Object connectionToken,
                                                     ResourcePackPolicyService policy) {
        if (playerId == null || connectionToken == null || policy == null || policy.enabled()
                || !policy.requiresHashes() || active.containsKey(playerId)) return false;
        active.put(playerId, new Active(connectionToken, null, policy, false));
        return true;
    }

    public synchronized ConnectionState connection(UUID playerId, Object connectionToken) {
        Active existing = playerId == null ? null : active.get(playerId);
        if (existing == null) return new ConnectionState(Status.BEFORE_BASELINE, null, null, false);
        if (existing.connectionToken != connectionToken) {
            return new ConnectionState(Status.WRONG_CONNECTION, null, null, false);
        }
        return new ConnectionState(Status.ACTIVE, existing.baseline, existing.policy,
                existing.serverPackBootstrapAllowed);
    }

    public synchronized boolean completeServerPackBootstrap(UUID playerId, Object connectionToken,
                                                             ValidationResult validation) {
        Active existing = playerId == null ? null : active.get(playerId);
        if (existing == null || existing.connectionToken != connectionToken
                || !existing.serverPackBootstrapAllowed || validation == null
                || !validation.isValid()) return false;
        PlayerPackSessionStore store = new PlayerPackSessionStore();
        store.storeValidated(playerId, validation);
        active.put(playerId, new Active(connectionToken, store.get(playerId).orElseThrow(),
                existing.policy, false));
        return true;
    }

    public synchronized void clear(UUID playerId, Object connectionToken) {
        Active existing = playerId == null ? null : active.get(playerId);
        if (existing != null && existing.connectionToken == connectionToken) active.remove(playerId);
    }

    public synchronized void clearAll() { active.clear(); }
    public synchronized int size() { return active.size(); }

    public enum Status { BEFORE_BASELINE, WRONG_CONNECTION, ACTIVE }

    public record ConnectionState(Status status, PlayerPackSessionStore.SessionBaseline baseline,
                                  ResourcePackPolicyService policy,
                                  boolean serverPackBootstrapAllowed) { }

    private record Active(Object connectionToken, PlayerPackSessionStore.SessionBaseline baseline,
                          ResourcePackPolicyService policy,
                          boolean serverPackBootstrapAllowed) { }
}
