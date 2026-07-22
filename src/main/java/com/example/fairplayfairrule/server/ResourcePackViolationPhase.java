package com.example.fairplayfairrule.server;

public enum ResourcePackViolationPhase {
    LOGIN("Login denied"),
    RUNTIME_RELOAD("Player disconnected");

    private final String action;
    ResourcePackViolationPhase(String action) { this.action = action; }
    public String action() { return action; }
}
