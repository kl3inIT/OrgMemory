package com.orgmemory.core.knowledge;

/**
 * Marks an object that no longer exists at the source. The connector retires the matching
 * {@code SourceObject} so it drops out of retrieval while its evidence is retained.
 *
 * @param externalObjectId the source id of the object to retire
 */
public record ConnectorTombstone(String externalObjectId) {

    public ConnectorTombstone {
        if (externalObjectId == null || externalObjectId.isBlank()) {
            throw new IllegalArgumentException("connector tombstone externalObjectId is required");
        }
        externalObjectId = externalObjectId.trim();
    }
}
