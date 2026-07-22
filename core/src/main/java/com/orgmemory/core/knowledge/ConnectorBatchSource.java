package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * Produces the crawl batches a connector driver should ingest, in order. This is the seam
 * between the source-specific adapter and the governed use case: the staging fixture source
 * reads committed JSON, and the live Slack adapter will implement the same port over the
 * Slack Web API, so nothing downstream of {@link ConnectorIngestionService} changes.
 * Re-ingesting a batch is safe: the ledger is idempotent (content materializes once, ACL
 * generations reconcile deterministically), so a driver may re-offer a batch without harm.
 */
public interface ConnectorBatchSource {

    List<ConnectorCrawlBatch> pendingBatches();
}
