package com.orgmemory.core.knowledge;

import java.util.UUID;

/**
 * The batch-level facts every per-object reconcile step needs: the tenant, the source
 * connection identity, the target Knowledge Space, the connection owner acting as author
 * and audit actor, and the opaque crawl cursor carried through as the audit request id.
 */
record ConnectorIngestionContext(
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        String crawlCursor) {

    static ConnectorIngestionContext from(ConnectorCrawlBatch batch) {
        return new ConnectorIngestionContext(
                batch.organizationId(),
                batch.sourceSystem(),
                batch.sourceConnectionKey(),
                batch.knowledgeSpaceId(),
                batch.actorUserId(),
                batch.crawlCursor());
    }
}
