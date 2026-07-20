package com.example.fairplayfairrule.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionGuardTest {
    @Test
    void matchesOnlyTheExactConnectionThatProducedTheSnapshot() {
        Object originalConnection = new Object();
        Object equalButDifferentConnection = new String("connection");
        Object anotherEqualButDifferentConnection = new String("connection");
        ClientConnectionGuard guard = new ClientConnectionGuard(originalConnection);

        assertTrue(guard.matches(originalConnection));
        assertFalse(guard.matches(null));
        assertFalse(new ClientConnectionGuard(equalButDifferentConnection)
                .matches(anotherEqualButDifferentConnection));
    }

    @Test
    void refusesMissingCapturedConnection() {
        assertThrows(IllegalArgumentException.class, () -> new ClientConnectionGuard(null));
    }
}
