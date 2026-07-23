package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what happened on each crawl batch, and reads it back for an administrator.
 *
 * <p>Recording runs in its own transaction. The interesting case is the failing one: the
 * transaction that was ingesting has already been marked for rollback, and joining it would
 * roll the record of the failure back along with the failure — leaving the screen saying
 * nothing went wrong. The row explaining a failure has to outlive the failure.
 */
@Service
public class ConnectorCrawlAttemptService {

    /** How much history a screen shows. Onyx paginates its attempts; one page is enough here. */
    private static final int RECENT_ATTEMPTS = 20;

    private final ConnectorCrawlAttemptRepository attempts;

    ConnectorCrawlAttemptService(ConnectorCrawlAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /** A batch that reconciled. Objects inside it may still have failed; the counts say so. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSucceeded(ConnectorCrawlBatch batch, ConnectorIngestionResult result) {
        Objects.requireNonNull(result, "result");
        save(batch, ConnectorCrawlOutcome.SUCCEEDED, result, null, null);
    }

    /** A batch refused for a reason retrying cannot change, and checkpointed past. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(ConnectorCrawlBatch batch, String errorCode, String errorMessage) {
        save(batch, ConnectorCrawlOutcome.REJECTED, null, errorCode, describe(errorMessage));
    }

    /** A batch left uncheckpointed, which the next poll will offer again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(ConnectorCrawlBatch batch, String errorCode, String errorMessage) {
        save(batch, ConnectorCrawlOutcome.FAILED, null, errorCode, describe(errorMessage));
    }

    /**
     * A connection the source could not read at all, so there was never a batch. Recorded
     * against the connection rather than a cursor, because there is no cursor for work that was
     * never offered.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnavailable(ConnectorConnectionFailure failure) {
        Objects.requireNonNull(failure, "failure");
        attempts.save(new ConnectorCrawlAttempt(
                failure.organizationId(),
                failure.sourceSystem(),
                failure.sourceConnectionKey(),
                null,
                ConnectorCrawlOutcome.UNAVAILABLE,
                null,
                failure.errorCode(),
                describe(failure.message()),
                Instant.now()));
    }

    /** The connection's recent attempts, newest first. */
    @Transactional(readOnly = true)
    public List<ConnectorCrawlAttemptView> recent(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return attempts
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyOrderByAttemptedAtDesc(
                        organizationId,
                        sourceSystem.trim(),
                        sourceConnectionKey.trim(),
                        Limit.of(RECENT_ATTEMPTS))
                .stream()
                .map(ConnectorCrawlAttempt::toView)
                .toList();
    }

    private void save(
            ConnectorCrawlBatch batch,
            ConnectorCrawlOutcome outcome,
            ConnectorIngestionResult result,
            String errorCode,
            String errorMessage) {
        Objects.requireNonNull(batch, "batch");
        attempts.save(new ConnectorCrawlAttempt(
                batch.organizationId(),
                batch.sourceSystem(),
                batch.sourceConnectionKey(),
                batch.crawlCursor(),
                outcome,
                result,
                errorCode,
                errorMessage,
                Instant.now()));
    }

    /**
     * A failure that carries no message still has to say something, because the database
     * refuses a failed attempt with nothing in it — and rightly: a row that records only that
     * something went wrong, with no indication of what, cannot be acted on. An exception with a
     * null message is common enough that the type name is the fallback.
     */
    private static String describe(String errorMessage) {
        return errorMessage == null || errorMessage.isBlank()
                ? "The crawl failed without reporting a reason."
                : errorMessage;
    }
}
