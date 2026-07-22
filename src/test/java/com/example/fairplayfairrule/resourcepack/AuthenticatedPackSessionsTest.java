package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedPackSessionsTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final String A = "a".repeat(64);
    private final AuthenticatedPackSessions sessions = new AuthenticatedPackSessions();
    private final ResourcePackPolicyService policy = ResourcePackPolicyService.load(true,
            List.of(), List.of(A), List.of(), List.of(), List.of()).policy();

    @AfterEach void clear() { sessions.clearAll(); }

    @Test
    void baselineExistsOnlyAfterSuccessfulValidation() {
        Object connection = new Object();
        ValidationResult rejected = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("bad.zip", "b".repeat(64), 3, ResourcePackType.ZIP)));
        assertFalse(sessions.establish(PLAYER, connection, rejected, policy));
        assertEquals(AuthenticatedPackSessions.Status.BEFORE_BASELINE,
                sessions.connection(PLAYER, connection).status());

        ValidationResult valid = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("good.zip", A, 3, ResourcePackType.ZIP)));
        assertTrue(sessions.establish(PLAYER, connection, valid, policy));
        assertEquals(AuthenticatedPackSessions.Status.ACTIVE,
                sessions.connection(PLAYER, connection).status());
    }

    @Test
    void staleConnectionCannotReadReplaceOrClearNewSession() {
        Object oldConnection = new Object();
        Object newConnection = new Object();
        ValidationResult valid = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("good.zip", A, 3, ResourcePackType.ZIP)));
        assertTrue(sessions.establish(PLAYER, oldConnection, valid, policy));
        assertFalse(sessions.establish(PLAYER, newConnection, valid, policy));
        assertEquals(AuthenticatedPackSessions.Status.WRONG_CONNECTION,
                sessions.connection(PLAYER, newConnection).status());
        sessions.clear(PLAYER, newConnection);
        assertEquals(AuthenticatedPackSessions.Status.ACTIVE,
                sessions.connection(PLAYER, oldConnection).status());
        sessions.clear(PLAYER, oldConnection);
        assertTrue(sessions.establish(PLAYER, newConnection, valid, policy));
    }

    @Test
    void policySnapshotSurvivesReloadAndShutdownClearsEverything() {
        Object connection = new Object();
        ValidationResult valid = policy.validateJoin(PLAYER, List.of(
                new ResourcePackManifestEntry("good.zip", A, 3, ResourcePackType.ZIP)));
        assertTrue(sessions.establish(PLAYER, connection, valid, policy));
        ResourcePackPolicyService replacement = ResourcePackPolicyService.load(true,
                List.of(), List.of(), List.of(), List.of(), List.of()).policy();
        assertSame(policy, sessions.connection(PLAYER, connection).policy());
        assertNotSame(replacement, sessions.connection(PLAYER, connection).policy());
        sessions.clearAll();
        assertEquals(0, sessions.size());
    }
}
