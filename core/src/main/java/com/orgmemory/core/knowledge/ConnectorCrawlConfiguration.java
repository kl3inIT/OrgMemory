package com.orgmemory.core.knowledge;

import java.time.Duration;
import java.util.UUID;

/**
 * Everything an adapter needs to crawl one connection, resolved from the ledger rather than
 * from configuration files. The tenant, the Space, and the actor have no equivalent at the
 * source, so they are decisions somebody made here and this record carries them.
 *
 * @param sourceConfig settings only this source system understands, as a JSON object. The
 *                     ledger carried it here without reading inside; the adapter that defined
 *                     its shape is the only thing that should parse it.
 */
public record ConnectorCrawlConfiguration(
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        String sourceConfig,
        Duration contentCrawlInterval) {

    public ConnectorCrawlConfiguration {
        sourceConfig = sourceConfig == null || sourceConfig.isBlank() ? "{}" : sourceConfig;
    }
}
