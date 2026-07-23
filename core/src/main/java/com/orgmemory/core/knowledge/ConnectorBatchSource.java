package com.orgmemory.core.knowledge;

/**
 * Produces the crawl batches a connector driver should ingest, in order. This is the seam
 * between a source-specific adapter and the governed use case: an adapter reads whatever its
 * source speaks and emits this contract, so nothing downstream of
 * {@link ConnectorIngestionService} learns anything about the source. What the ledger does
 * know about a source is the {@link ConnectorSourceProfile} that adapter contributed.
 * Re-ingesting a batch is safe: the ledger is idempotent (content materializes once, ACL
 * generations reconcile deterministically), so a driver may re-offer a batch without harm.
 *
 * <p>A poll reports the connections it could not reach as well as the batches it produced. An
 * adapter that swallowed those would leave the driver unable to tell "nothing changed" from
 * "nothing could be read", which are the two answers an administrator most needs separated.
 */
public interface ConnectorBatchSource {

    ConnectorPoll pendingBatches();
}
