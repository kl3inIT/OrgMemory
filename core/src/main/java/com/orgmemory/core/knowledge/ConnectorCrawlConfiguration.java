package com.orgmemory.core.knowledge;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Everything an adapter needs to crawl one connection, resolved from the ledger rather than
 * from configuration files. The tenant, the Space, and the actor have no equivalent at the
 * source, so they are decisions somebody made here and this record carries them.
 *
 * @param channels channel names to crawl; empty means every channel the connector can see
 */
public record ConnectorCrawlConfiguration(
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        List<String> channels,
        Duration contentCrawlInterval,
        int maxThreadsPerChannel) {

    public ConnectorCrawlConfiguration {
        channels = channels == null ? List.of() : List.copyOf(channels);
    }
}
