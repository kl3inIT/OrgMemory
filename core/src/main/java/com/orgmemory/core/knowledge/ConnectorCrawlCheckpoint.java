package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The last crawl cursor one source connection got through. The cursor is opaque to
 * OrgMemory — only the batch producer knows what it points at — so this row carries no
 * interpretation of it, just the fact that everything up to it has been dealt with.
 */
@Entity
@Table(name = "connector_crawl_checkpoints")
class ConnectorCrawlCheckpoint extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "crawl_cursor", nullable = false, length = 512)
    private String crawlCursor;

    @Column(name = "checkpointed_at", nullable = false)
    private Instant checkpointedAt;

    protected ConnectorCrawlCheckpoint() {
    }

    ConnectorCrawlCheckpoint(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String crawlCursor,
            Instant checkpointedAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.crawlCursor = crawlCursor;
        this.checkpointedAt = checkpointedAt;
    }

    void advanceTo(String crawlCursor, Instant checkpointedAt) {
        this.crawlCursor = crawlCursor;
        this.checkpointedAt = checkpointedAt;
    }

    String getCrawlCursor() {
        return crawlCursor;
    }

    Instant getCheckpointedAt() {
        return checkpointedAt;
    }
}
