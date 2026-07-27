package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable membership evidence for one source group and generation. */
@Entity
@Table(name = "source_group_membership_snapshots")
class SourceGroupMembershipSnapshot extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "sync_run_id", nullable = false, updatable = false)
    private UUID syncRunId;

    @Column(name = "group_principal_id", nullable = false, updatable = false)
    private UUID groupPrincipalId;

    @Column(name = "membership_generation", nullable = false, updatable = false)
    private long membershipGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_status", nullable = false, length = 16, updatable = false)
    private ConnectorCaptureStatus captureStatus;

    @Column(name = "incomplete_reason", length = 128, updatable = false)
    private String incompleteReason;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    protected SourceGroupMembershipSnapshot() {
    }

    SourceGroupMembershipSnapshot(
            UUID organizationId,
            UUID syncRunId,
            UUID groupPrincipalId,
            long membershipGeneration,
            ConnectorCaptureStatus captureStatus,
            String incompleteReason,
            Instant capturedAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.syncRunId = syncRunId;
        this.groupPrincipalId = groupPrincipalId;
        this.membershipGeneration = membershipGeneration;
        this.captureStatus = captureStatus;
        this.incompleteReason = incompleteReason;
        this.capturedAt = capturedAt;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getGroupPrincipalId() {
        return groupPrincipalId;
    }

    long getMembershipGeneration() {
        return membershipGeneration;
    }

    ConnectorCaptureStatus getCaptureStatus() {
        return captureStatus;
    }
}
