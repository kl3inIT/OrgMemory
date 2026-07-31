package com.orgmemory.core.knowledge.connector;

/** The non-secret adapter settings for one organization connection. */
public record ConnectorConnectionConfiguration(
        String sourceSystem,
        String sourceConnectionKey,
        String sourceConfig,
        boolean credentialSet) {

    public ConnectorConnectionConfiguration {
        sourceConfig = sourceConfig == null || sourceConfig.isBlank() ? "{}" : sourceConfig;
    }
}
