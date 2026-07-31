package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pack_assignments")
class PackAssignment extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "pack_asset_id", nullable = false, updatable = false)
    private UUID packAssetId;

    @Column(name = "pack_release_id", nullable = false, updatable = false)
    private UUID packReleaseId;

    @Column(name = "release_digest", nullable = false, length = 64, updatable = false)
    private String releaseDigest;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PackAssignmentStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PackAssignment() {
    }

    PackAssignment(
            UUID organizationId,
            UUID packAssetId,
            UUID packReleaseId,
            String releaseDigest,
            UUID actorUserId,
            Instant startedAt) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.packAssetId = Objects.requireNonNull(packAssetId, "packAssetId");
        this.packReleaseId = Objects.requireNonNull(packReleaseId, "packReleaseId");
        this.releaseDigest = Objects.requireNonNull(releaseDigest, "releaseDigest");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.status = PackAssignmentStatus.IN_PROGRESS;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    void complete(Instant timestamp) {
        if (status == PackAssignmentStatus.COMPLETED) {
            return;
        }
        status = PackAssignmentStatus.COMPLETED;
        completedAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    PackAssignmentStatus getStatus() {
        return status;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
