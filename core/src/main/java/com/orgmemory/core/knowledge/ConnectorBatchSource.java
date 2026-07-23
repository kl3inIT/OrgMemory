package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * Produces the crawl batches a connector driver should ingest, in order. This is the seam
 * between a source-specific adapter and the governed use case: an adapter reads whatever its
 * source speaks and emits this contract, so nothing downstream of
 * {@link ConnectorIngestionService} learns anything about the source. What the ledger does
 * know about a source is the {@link ConnectorSourceProfile} that adapter contributed.
 * Re-ingesting a batch is safe: the ledger is idempotent (content materializes once, ACL
 * generations reconcile deterministically), so a driver may re-offer a batch without harm.
 */
public interface ConnectorBatchSource {

    List<ConnectorCrawlBatch> pendingBatches();
}
