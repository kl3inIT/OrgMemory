package com.orgmemory.core.knowledge.connector;



import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Last observed and successfully reconciled state for one independently checkpointed
 * connector component.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 16, updatable = false)
    private ConnectorSyncComponent component;

    @Column(name = "observed_cursor", nullable = false, length = 512)
    private String observedCursor;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_status", nullable = false, length = 16)
    private ConnectorCaptureStatus captureStatus;

    @Column(name = "incomplete_reason", length = 500)
    private String incompleteReason;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "last_successful_cursor", length = 512)
    private String lastSuccessfulCursor;

    @Column(name = "last_successful_at")
    private Instant lastSuccessfulAt;

    protected ConnectorCrawlCheckpoint() {
    }

    ConnectorCrawlCheckpoint(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            ConnectorComponentState state,
            Instant observedAt,
            boolean reconciled) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.component = state.component();
        advanceTo(state, observedAt, reconciled);
    }

    void advanceTo(ConnectorComponentState state, Instant observedAt, boolean reconciled) {
        this.observedCursor = state.cursor();
        this.captureStatus = state.captureStatus();
        this.incompleteReason = state.incompleteReason();
        this.observedAt = observedAt;
        if (reconciled && state.captureStatus() == ConnectorCaptureStatus.COMPLETE) {
            this.lastSuccessfulCursor = state.cursor();
            this.lastSuccessfulAt = observedAt;
        }
    }

    ConnectorSyncComponent getComponent() {
        return component;
    }

    String getObservedCursor() {
        return observedCursor;
    }

    ConnectorComponentCheckpointView toView() {
        return new ConnectorComponentCheckpointView(
                component,
                observedCursor,
                captureStatus,
                incompleteReason,
                observedAt,
                lastSuccessfulCursor,
                lastSuccessfulAt);
    }
}
