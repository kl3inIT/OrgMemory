package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.shared.BaseEntity;
import com.orgmemory.core.shared.error.BusinessConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mutable compare-and-set pointer to one group's current sealed complete snapshot. */
@Entity
@Table(name = "source_group_membership_heads")
public class SourceGroupMembershipHead extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "group_principal_id", nullable = false, updatable = false)
    private UUID groupPrincipalId;

    @Column(name = "current_snapshot_id", nullable = false)
    private UUID currentSnapshotId;

    @Column(name = "membership_generation", nullable = false)
    private long membershipGeneration;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    protected SourceGroupMembershipHead() {
    }

    SourceGroupMembershipHead(SourceGroupMembershipSnapshot snapshot, Instant activatedAt) {
        super(UUID.randomUUID());
        this.organizationId = snapshot.getOrganizationId();
        this.groupPrincipalId = snapshot.getGroupPrincipalId();
        advance(snapshot, activatedAt);
    }

    void advance(SourceGroupMembershipSnapshot snapshot, Instant activatedAt) {
        if (snapshot.getCaptureStatus() != SourceMembershipCaptureStatus.COMPLETE) {
            throw new IllegalArgumentException(
                    "only complete source group membership may become active");
        }
        if (!organizationId.equals(snapshot.getOrganizationId())
                || !groupPrincipalId.equals(snapshot.getGroupPrincipalId())) {
            throw new IllegalArgumentException(
                    "membership head and snapshot must name the same organization and group");
        }
        if (currentSnapshotId != null
                && snapshot.getMembershipGeneration() <= membershipGeneration) {
            throw new BusinessConflictException(
                    "knowledge-ingestion.conflict",
                    "membership generation must advance monotonically");
        }
        this.currentSnapshotId = snapshot.getId();
        this.membershipGeneration = snapshot.getMembershipGeneration();
        this.activatedAt = activatedAt;
    }

    UUID getCurrentSnapshotId() {
        return currentSnapshotId;
    }

    long getMembershipGeneration() {
        return membershipGeneration;
    }
}
