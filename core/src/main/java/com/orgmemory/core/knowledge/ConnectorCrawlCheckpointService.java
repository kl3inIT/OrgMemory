package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers how far each source connection has been crawled, so a driver that restarts
 * mid-run does not replay from the beginning of whatever the producer still holds.
 *
 * <p>A checkpoint is deliberately not a correctness guarantee. Re-ingesting a batch is safe —
 * content materializes once per revision and ACL generations reconcile deterministically — so
 * this exists to avoid pointless work, not to prevent harm. It records only the connection's
 * most recent cursor rather than every cursor ever seen, because that is what a resuming
 * producer needs and what a growing ledger of dead cursors does not give.
 */
@Service
public class ConnectorCrawlCheckpointService {

    private final ConnectorCrawlCheckpointRepository checkpoints;

    ConnectorCrawlCheckpointService(ConnectorCrawlCheckpointRepository checkpoints) {
        this.checkpoints = checkpoints;
    }

    /** Whether this exact cursor is the one the connection last completed. */
    @Transactional(readOnly = true)
    public boolean isCompleted(ConnectorCrawlBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return find(batch)
                .map(checkpoint -> checkpoint.getCrawlCursor().equals(batch.crawlCursor()))
                .orElse(false);
    }

    /** The cursor a connection last completed, for a producer that resumes from it. */
    @Transactional(readOnly = true)
    public Optional<String> lastCompletedCursor(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return checkpoints
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
                        organizationId, sourceSystem.trim(), sourceConnectionKey.trim())
                .map(ConnectorCrawlCheckpoint::getCrawlCursor);
    }

    /**
     * Marks the batch dealt with. Called both when a batch ingested and when it was rejected
     * for a reason retrying cannot change: a poisoned batch that stays unrecorded would be
     * re-offered on every poll forever.
     */
    @Transactional
    public void complete(ConnectorCrawlBatch batch) {
        Objects.requireNonNull(batch, "batch");
        Instant now = Instant.now();
        ConnectorCrawlCheckpoint checkpoint = find(batch).orElse(null);
        if (checkpoint == null) {
            checkpoints.save(new ConnectorCrawlCheckpoint(
                    batch.organizationId(),
                    batch.sourceSystem(),
                    batch.sourceConnectionKey(),
                    batch.crawlCursor(),
                    now));
            return;
        }
        checkpoint.advanceTo(batch.crawlCursor(), now);
        checkpoints.save(checkpoint);
    }

    private Optional<ConnectorCrawlCheckpoint> find(ConnectorCrawlBatch batch) {
        return checkpoints.findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
                batch.organizationId(), batch.sourceSystem(), batch.sourceConnectionKey());
    }
}
