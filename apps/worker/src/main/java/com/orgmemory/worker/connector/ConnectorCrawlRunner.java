package com.orgmemory.worker.connector;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlCheckpointService;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import com.orgmemory.core.knowledge.UnsupportedConnectorPayloadException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drives the connector: pulls pending batches from every {@link ConnectorBatchSource} and
 * ingests each through {@link ConnectorIngestionService}. Progress is checkpointed per
 * connection, so a driver that restarts resumes instead of replaying everything the producers
 * still hold.
 *
 * <p>Sources are taken as a list because a deployment can run more than one — committed
 * fixtures and a live workspace are both present in development — and because a source that
 * cannot produce right now, whether it is rate limited or unreachable, must not stop the others
 * from running.
 *
 * <p>A batch the ingestion service rejects is dealt with by what the rejection means. An
 * unsupported payload version or an invalid envelope will read the same on every attempt, so it
 * is checkpointed past — leaving a poisoned batch unrecorded would make every poll retry it
 * forever. Anything else is treated as transient: it is retried a bounded number of times
 * within this run and, failing that, left uncheckpointed for the next poll.
 */
@Component
class ConnectorCrawlRunner {

    private static final Logger log = LoggerFactory.getLogger(ConnectorCrawlRunner.class);
    private static final int MAX_ATTEMPTS = 3;

    private final List<ConnectorBatchSource> sources;
    private final ConnectorIngestionService ingestion;
    private final ConnectorCrawlCheckpointService checkpoints;

    ConnectorCrawlRunner(
            List<ConnectorBatchSource> sources,
            ConnectorIngestionService ingestion,
            ConnectorCrawlCheckpointService checkpoints) {
        this.sources = List.copyOf(sources);
        this.ingestion = ingestion;
        this.checkpoints = checkpoints;
    }

    void runPending() {
        for (ConnectorBatchSource source : sources) {
            List<ConnectorCrawlBatch> pending;
            try {
                pending = source.pendingBatches();
            } catch (RuntimeException unavailable) {
                log.warn("Connector source {} could not produce batches this poll: {}",
                        source.getClass().getSimpleName(), unavailable.getMessage());
                continue;
            }
            for (ConnectorCrawlBatch batch : pending) {
                if (checkpoints.isCompleted(batch)) {
                    continue;
                }
                ingestWithRetry(batch);
            }
        }
    }

    private void ingestWithRetry(ConnectorCrawlBatch batch) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                report(batch, ingestion.ingest(batch));
                checkpoints.complete(batch);
                return;
            } catch (UnsupportedConnectorPayloadException | IllegalArgumentException permanent) {
                log.warn("Connector batch {} was rejected and will not be retried: {}",
                        batch.crawlCursor(), permanent.getMessage());
                checkpoints.complete(batch);
                return;
            } catch (RuntimeException transientFailure) {
                log.warn("Connector batch {} failed on attempt {} of {}: {}",
                        batch.crawlCursor(), attempt, MAX_ATTEMPTS, transientFailure.getMessage());
            }
        }
        log.warn("Connector batch {} exhausted {} attempts and stays pending for the next poll",
                batch.crawlCursor(), MAX_ATTEMPTS);
    }

    private static void report(ConnectorCrawlBatch batch, ConnectorIngestionResult result) {
        log.info(
                "Connector batch {} ingested: materialized={} rotated={} rematerialized={} "
                        + "retired={} failures={}",
                batch.crawlCursor(),
                result.materialized().size(),
                result.rotated().size(),
                result.rematerialized().size(),
                result.retired().size(),
                result.failures().size());
        result.failures().forEach(failure -> log.warn(
                "Connector batch {} object {} failed: {}",
                batch.crawlCursor(),
                failure.externalObjectId(),
                failure.reason()));
    }
}
