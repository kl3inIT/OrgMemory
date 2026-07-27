package com.orgmemory.core.knowledge;

import java.util.Set;

/**
 * One object that failed to reconcile, isolated so the rest of the batch proceeds. The
 * reason is a short diagnostic, not sensitive content.
 */
public record ConnectorItemFailure(
        String externalObjectId,
        String reason,
        Set<ConnectorSyncComponent> components) {

    public ConnectorItemFailure {
        components = Set.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("connector failure components are required");
        }
    }

    public ConnectorItemFailure(
            String externalObjectId,
            String reason,
            ConnectorSyncComponent... components) {
        this(externalObjectId, reason, Set.of(components));
    }
}
