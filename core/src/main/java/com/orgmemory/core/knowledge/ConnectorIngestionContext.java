package com.orgmemory.core.knowledge;

import java.util.UUID;

/**
 * The batch-level facts every per-object reconcile step needs: the tenant, the source
 * connection identity, the target Knowledge Space, the connection owner acting as author
 * and audit actor, and the opaque crawl cursor carried through as the audit request id.
 *
 * <p>It carries the source's profile too, resolved once when the batch is accepted rather
 * than looked up per object. That is also what keeps the reconciler free of any source's
 * name: everything it needs to know about where this batch came from is in the profile.
 */
record ConnectorIngestionContext(
        UUID organizationId,
        ConnectorSourceProfile profile,
        String sourceConnectionKey,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        String crawlCursor) {

    static ConnectorIngestionContext from(ConnectorCrawlBatch batch, ConnectorSourceProfile profile) {
        return new ConnectorIngestionContext(
                batch.organizationId(),
                profile,
                batch.sourceConnectionKey(),
                batch.knowledgeSpaceId(),
                batch.actorUserId(),
                batch.crawlCursor());
    }

    String sourceSystem() {
        return profile.sourceSystem();
    }
}
