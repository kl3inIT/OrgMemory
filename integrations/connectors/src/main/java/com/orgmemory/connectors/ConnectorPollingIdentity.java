package com.orgmemory.connectors;

import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import java.util.Objects;
import java.util.UUID;

/** Canonical tenant/source/connection identity for in-memory polling state. */
record ConnectorPollingIdentity(
        UUID organizationId, String sourceSystem, String sourceConnectionKey) {

    ConnectorPollingIdentity {
        Objects.requireNonNull(organizationId, "organizationId");
        sourceSystem = requireText(sourceSystem, "sourceSystem");
        sourceConnectionKey = requireText(sourceConnectionKey, "sourceConnectionKey");
    }

    static ConnectorPollingIdentity of(
            ConnectorCrawlConfiguration configuration, String sourceSystem) {
        return new ConnectorPollingIdentity(
                configuration.organizationId(), sourceSystem, configuration.sourceConnectionKey());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
