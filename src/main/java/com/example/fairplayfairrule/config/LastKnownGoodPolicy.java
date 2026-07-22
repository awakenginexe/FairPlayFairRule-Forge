package com.example.fairplayfairrule.config;

import com.example.fairplayfairrule.resourcepack.ResourcePackPolicyService;

import java.util.Objects;

/** Atomically retains the previous immutable policy when a candidate is invalid. */
public final class LastKnownGoodPolicy {
    private volatile ResourcePackPolicyService current;

    public LastKnownGoodPolicy(ResourcePackPolicyService initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    public synchronized boolean accept(ResourcePackPolicyService.PolicyLoadResult candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.valid()) return false;
        current = candidate.policy();
        return true;
    }

    public ResourcePackPolicyService current() { return current; }
}
