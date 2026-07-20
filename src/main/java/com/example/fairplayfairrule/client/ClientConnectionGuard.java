package com.example.fairplayfairrule.client;

/** Binds an asynchronously hashed report to the exact client connection that requested it. */
final class ClientConnectionGuard {
    private final Object connectionIdentity;

    ClientConnectionGuard(Object connectionIdentity) {
        if (connectionIdentity == null) {
            throw new IllegalArgumentException("A live connection is required");
        }
        this.connectionIdentity = connectionIdentity;
    }

    boolean matches(Object currentConnection) {
        return connectionIdentity == currentConnection;
    }
}
