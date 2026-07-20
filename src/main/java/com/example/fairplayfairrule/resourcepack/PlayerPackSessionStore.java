package com.example.fairplayfairrule.resourcepack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side store for exact resource-pack sets validated at session start. */
public final class PlayerPackSessionStore {
    private final Map<UUID, SessionBaseline> sessions = new ConcurrentHashMap<>();

    public void storeValidated(UUID playerId, ValidationResult result) {
        if (!result.isValid()) {
            throw new IllegalArgumentException("Cannot establish a baseline from failed validation");
        }
        sessions.put(playerId, new SessionBaseline(result.activeHashes(), result.namesByHash()));
    }

    public Optional<SessionBaseline> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    public record SessionBaseline(Set<String> hashes, Map<String, String> namesByHash) {
        public SessionBaseline {
            hashes = Set.copyOf(hashes);
            namesByHash = Map.copyOf(namesByHash);
        }
    }
}
