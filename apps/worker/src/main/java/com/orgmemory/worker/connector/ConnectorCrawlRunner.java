package com.orgmemory.worker.connector;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorCrawlAttemptService;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlCheckpointService;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import com.orgmemory.core.knowledge.ConnectorPoll;
import com.orgmemory.core.knowledge.UnsupportedConnectorPayloadException;
import com.orgmemory.core.knowledge.UnsupportedConnectorSourceException;
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
    private final ConnectorCrawlAttemptService attempts;

    ConnectorCrawlRunner(
            List<ConnectorBatchSource> sources,
            ConnectorIngestionService ingestion,
            ConnectorCrawlCheckpointService checkpoints,
            ConnectorCrawlAttemptService attempts) {
        this.sources = List.copyOf(sources);
        this.ingestion = ingestion;
        this.checkpoints = checkpoints;
        this.attempts = attempts;
    }

    void runPending() {
        for (ConnectorBatchSource source : sources) {
            ConnectorPoll poll;
            try {
                poll = source.pendingBatches();
            } catch (RuntimeException unavailable) {
                log.warn("Connector source {} could not produce batches this poll: {}",
                        source.getClass().getSimpleName(), unavailable.getMessage());
                continue;
            }
            // Recorded before the batches are ingested, so a connection that could not be read
            // is explained even if ingesting the others takes a while or the process dies part
            // way through.
            poll.unavailable().forEach(attempts::recordUnavailable);
            for (ConnectorCrawlBatch batch : poll.batches()) {
                if (checkpoints.isCompleted(batch)) {
                    continue;
                }
                ingestWithRetry(batch);
            }
        }
    }

    private void ingestWithRetry(ConnectorCrawlBatch batch) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ConnectorIngestionResult result = ingestion.ingest(batch);
                report(batch, result);
                attempts.recordSucceeded(batch, result);
                checkpoints.complete(batch);
                return;
            } catch (UnsupportedConnectorPayloadException | UnsupportedConnectorSourceException
                    | IllegalArgumentException permanent) {
                log.warn("Connector batch {} was rejected and will not be retried: {}",
                        batch.crawlCursor(), permanent.getMessage());
                attempts.recordRejected(batch, codeOf(permanent), permanent.getMessage());
                checkpoints.complete(batch);
                return;
            } catch (RuntimeException transientFailure) {
                log.warn("Connector batch {} failed on attempt {} of {}: {}",
                        batch.crawlCursor(), attempt, MAX_ATTEMPTS, transientFailure.getMessage());
                lastFailure = transientFailure;
            }
        }
        log.warn("Connector batch {} exhausted {} attempts and stays pending for the next poll",
                batch.crawlCursor(), MAX_ATTEMPTS);
        // One row per exhausted batch rather than one per attempt. The attempts inside a single
        // poll fail the same way; what an administrator needs is that this batch is stuck and
        // what it said, not the same sentence three times.
        attempts.recordFailed(batch, codeOf(lastFailure), messageOf(lastFailure));
    }

    /**
     * A machine-readable label for what went wrong. The exception's simple name is the honest
     * answer: the driver sees an arbitrary runtime failure and inventing a taxonomy over it
     * would be a guess, whereas the type is exactly what was thrown.
     */
    private static String codeOf(RuntimeException failure) {
        return failure == null ? null : failure.getClass().getSimpleName();
    }

    private static String messageOf(RuntimeException failure) {
        return failure == null ? null : failure.getMessage();
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
