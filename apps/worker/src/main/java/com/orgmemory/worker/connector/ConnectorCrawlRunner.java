package com.orgmemory.worker.connector;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drives the staging connector: pulls pending batches from the {@link ConnectorBatchSource} and
 * ingests each through {@link ConnectorIngestionService}. Batches are keyed by crawl cursor and
 * ingested at most once per process; because the ledger is idempotent, re-offering a batch is
 * harmless. A batch whose envelope is rejected is logged and skipped rather than aborting the
 * run. Durable cross-restart cursors and bounded per-batch retry are deferred to the live
 * increment; the reconciler already isolates per-object failures within a batch.
 */
@Component
class ConnectorCrawlRunner {

    private static final Logger log = LoggerFactory.getLogger(ConnectorCrawlRunner.class);

    private final ConnectorBatchSource source;
    private final ConnectorIngestionService ingestion;
    private final Set<String> processedCursors = ConcurrentHashMap.newKeySet();

    ConnectorCrawlRunner(ConnectorBatchSource source, ConnectorIngestionService ingestion) {
        this.source = source;
        this.ingestion = ingestion;
    }

    void runPending() {
        for (ConnectorCrawlBatch batch : source.pendingBatches()) {
            if (!processedCursors.add(batch.crawlCursor())) {
                continue;
            }
            try {
                ConnectorIngestionResult result = ingestion.ingest(batch);
                log.info(
                        "Connector batch {} ingested: materialized={} rotated={} deferred={} retired={} failures={}",
                        batch.crawlCursor(),
                        result.materialized().size(),
                        result.rotated().size(),
                        result.contentDeferred().size(),
                        result.retired().size(),
                        result.failures().size());
                result.failures().forEach(failure -> log.warn(
                        "Connector batch {} object {} failed: {}",
                        batch.crawlCursor(),
                        failure.externalObjectId(),
                        failure.reason()));
            } catch (RuntimeException failure) {
                log.warn("Connector batch {} was rejected and skipped: {}",
                        batch.crawlCursor(), failure.getMessage());
            }
        }
    }
}
