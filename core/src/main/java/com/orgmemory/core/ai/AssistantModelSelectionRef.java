package com.orgmemory.core.ai;

import java.util.UUID;

public record AssistantModelSelectionRef(
        UUID activationId,
        UUID routeOverrideId,
        long routeOverrideVersion) {

    public AssistantModelSelectionRef {
        if (activationId == null || routeOverrideId == null || routeOverrideVersion < 0) {
            throw new IllegalArgumentException("A complete Assistant model selection is required");
        }
    }
}
