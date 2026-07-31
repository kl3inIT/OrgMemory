package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable provenance shared by every group-membership snapshot captured in one batch. */
@Entity
@Table(name = "source_membership_sync_runs")
public class SourceMembershipSyncRun extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    protected SourceMembershipSyncRun() {
    }

    public SourceMembershipSyncRun(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            Instant capturedAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.capturedAt = capturedAt;
    }
}
