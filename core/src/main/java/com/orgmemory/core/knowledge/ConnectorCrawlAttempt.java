package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * What happened the last time a driver acted on one crawl batch.
 *
 * <p>Every field is written once, at the end of the attempt, and never revised — an attempt
 * that already happened cannot become a different attempt. There are no setters for that
 * reason.
 *
 * <p>{@code errorMessage} is a diagnostic. The credential an adapter authenticates with
 * travels in an {@code Authorization} header rather than a URI or a form body, and adapter
 * exceptions carry the method and the source's own error code, so nothing that reaches here
 * has ever contained a secret. Any adapter that changes that has to change it here first.
 */
@Entity
@Table(name = "connector_crawl_attempts")
class ConnectorCrawlAttempt extends BaseEntity {

    private static final int MAX_ERROR_MESSAGE = 512;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "crawl_cursor", length = 512, updatable = false)
    private String crawlCursor;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32, updatable = false)
    private ConnectorCrawlOutcome outcome;

    @Column(name = "objects_materialized", nullable = false, updatable = false)
    private int objectsMaterialized;

    @Column(name = "objects_rotated", nullable = false, updatable = false)
    private int objectsRotated;

    @Column(name = "objects_rematerialized", nullable = false, updatable = false)
    private int objectsRematerialized;

    @Column(name = "objects_retired", nullable = false, updatable = false)
    private int objectsRetired;

    @Column(name = "objects_failed", nullable = false, updatable = false)
    private int objectsFailed;

    @Column(name = "error_code", length = 64, updatable = false)
    private String errorCode;

    @Column(name = "error_message", length = 512, updatable = false)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected ConnectorCrawlAttempt() {
    }

    ConnectorCrawlAttempt(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String crawlCursor,
            ConnectorCrawlOutcome outcome,
            ConnectorIngestionResult result,
            String errorCode,
            String errorMessage,
            Instant attemptedAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.crawlCursor = crawlCursor;
        this.outcome = outcome;
        this.objectsMaterialized = result == null ? 0 : result.materialized().size();
        this.objectsRotated = result == null ? 0 : result.rotated().size();
        this.objectsRematerialized = result == null ? 0 : result.rematerialized().size();
        this.objectsRetired = result == null ? 0 : result.retired().size();
        this.objectsFailed = result == null ? 0 : result.failures().size();
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage);
        this.attemptedAt = attemptedAt;
    }

    /**
     * A message longer than the column is stored cut rather than rejected. Losing the tail of a
     * diagnostic is a smaller loss than losing the row that says the crawl failed at all.
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE ? message : message.substring(0, MAX_ERROR_MESSAGE);
    }

    ConnectorCrawlAttemptView toView() {
        return new ConnectorCrawlAttemptView(
                crawlCursor,
                outcome,
                objectsMaterialized,
                objectsRotated,
                objectsRematerialized,
                objectsRetired,
                objectsFailed,
                errorCode,
                errorMessage,
                attemptedAt);
    }
}
