package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.consumption.AssetAvailability;

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
@Table(name = "asset_release_availability_events")
class AssetReleaseAvailabilityEvent extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AssetAvailability availability;

    @Column(nullable = false, length = 1024, updatable = false)
    private String reason;

    @Column(name = "changed_by_user_id", nullable = false, updatable = false)
    private UUID changedByUserId;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    protected AssetReleaseAvailabilityEvent() {
    }

    AssetReleaseAvailabilityEvent(
            AssetRelease release,
            AssetAvailability availability,
            String reason,
            UUID changedByUserId,
            Instant effectiveAt) {
        super(UUID.randomUUID());
        this.organizationId = release.getOrganizationId();
        this.assetId = release.getAssetId();
        this.releaseId = release.getId();
        this.availability = Objects.requireNonNull(availability, "availability");
        this.reason = requireReason(reason);
        this.changedByUserId = Objects.requireNonNull(changedByUserId, "changedByUserId");
        this.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
    }

    UUID getReleaseId() {
        return releaseId;
    }

    AssetAvailability getAvailability() {
        return availability;
    }

    String getReason() {
        return reason;
    }

    UUID getChangedByUserId() {
        return changedByUserId;
    }

    Instant getEffectiveAt() {
        return effectiveAt;
    }

    private static String requireReason(String reason) {
        String normalized = Objects.requireNonNull(reason, "reason").trim();
        if (normalized.isEmpty() || normalized.length() > 1024) {
            throw new IllegalArgumentException(
                    "reason must contain between 1 and 1024 characters");
        }
        return normalized;
    }
}
