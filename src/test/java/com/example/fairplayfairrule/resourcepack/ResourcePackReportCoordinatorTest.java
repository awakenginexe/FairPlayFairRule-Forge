package com.example.fairplayfairrule.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackReportCoordinatorTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String LOCAL = "a".repeat(64);
    private static final String SERVER = "b".repeat(64);
    private static final String BANNED = "c".repeat(64);

    @Test
    void disabledIntegrityStillMonitorsRuntimeBansWithoutLockingPacks() {
        var sessions = new AuthenticatedPackSessions();
        var policy = ResourcePackPolicyService.load(false, List.of(), List.of(), List.of(),
                List.of(), List.of(BANNED)).policy();
        Object connection = new Object();

        var joined = ResourcePackReportCoordinator.join(policy, sessions, PLAYER, connection,
                List.of(zip("first.zip", LOCAL)), false);
        assertTrue(joined.validation().isValid());
        assertFalse(joined.baselineEstablished());
        assertNull(sessions.connection(PLAYER, connection).baseline());

        var changedAllowed = ResourcePackReportCoordinator.reload(sessions, PLAYER, connection,
                List.of(zip("different.zip", SERVER)));
        assertTrue(changedAllowed.validation().isValid());
        assertFalse(changedAllowed.ignored());

        var banned = ResourcePackReportCoordinator.reload(sessions, PLAYER, connection,
                List.of(zip("banned.zip", BANNED)));
        assertEquals(ValidationFailureCode.BANNED_PACK, banned.validation().code());
    }

    @Test
    void unchangedReloadDoesNotConsumeServerPackBootstrap() {
        var sessions = new AuthenticatedPackSessions();
        var policy = ResourcePackPolicyService.load(true, List.of(), List.of(LOCAL), List.of(),
                List.of(SERVER), List.of()).policy();
        Object connection = new Object();

        var joined = ResourcePackReportCoordinator.join(policy, sessions, PLAYER, connection,
                List.of(zip("local.zip", LOCAL)), true);
        assertTrue(joined.baselineEstablished());

        var unchanged = ResourcePackReportCoordinator.reload(sessions, PLAYER, connection,
                List.of(zip("local.zip", LOCAL)));
        assertTrue(unchanged.validation().isValid());
        assertTrue(sessions.connection(PLAYER, connection).serverPackBootstrapAllowed());

        var applied = ResourcePackReportCoordinator.reload(sessions, PLAYER, connection, List.of(
                zip("local.zip", LOCAL), new ResourcePackManifestEntry("server", SERVER, 20,
                        ResourcePackType.SERVER_DOWNLOADED)));
        assertTrue(applied.validation().isValid());
        assertFalse(sessions.connection(PLAYER, connection).serverPackBootstrapAllowed());
        assertEquals(List.of("ZIP:" + LOCAL, "SERVER_DOWNLOADED:" + SERVER),
                sessions.connection(PLAYER, connection).baseline().orderedStateTokens());
    }

    private static ResourcePackManifestEntry zip(String name, String hash) {
        return new ResourcePackManifestEntry(name, hash, 20, ResourcePackType.ZIP);
    }
}
