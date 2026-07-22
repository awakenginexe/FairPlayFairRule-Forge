package com.example.fairplayfairrule.resourcepack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side store for exact resource-pack sets validated at session start. */
public final class PlayerPackSessionStore {
    private final Map<UUID, SessionBaseline> sessions = new ConcurrentHashMap<>();

    public void storeValidated(UUID playerId, ValidationResult result) {
        if (!result.isValid()) {
            throw new IllegalArgumentException("Cannot establish a baseline from failed validation");
        }
        sessions.put(playerId, new SessionBaseline(result.orderedStateTokens(),
                result.namesByStateToken(), result.normalizedManifest()));
    }

    public Optional<SessionBaseline> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clearAll() { sessions.clear(); }
    public int size() { return sessions.size(); }

    public record SessionBaseline(List<String> orderedStateTokens,
                                  Map<String, String> namesByStateToken,
                                  List<ResourcePackManifestEntry> manifest) {
        public SessionBaseline {
            orderedStateTokens = List.copyOf(orderedStateTokens);
            namesByStateToken = Map.copyOf(namesByStateToken);
            manifest = List.copyOf(manifest);
        }
        public List<String> orderedHashes() {
            return orderedStateTokens.stream().map(ValidationResult::hashFromStateToken).toList();
        }
        public List<String> hashes() { return orderedHashes(); }
        public Map<String, String> namesByHash() {
            java.util.LinkedHashMap<String, String> names = new java.util.LinkedHashMap<>();
            namesByStateToken.forEach((token, name) ->
                    names.put(ValidationResult.hashFromStateToken(token), name));
            return Map.copyOf(names);
        }
    }
}
