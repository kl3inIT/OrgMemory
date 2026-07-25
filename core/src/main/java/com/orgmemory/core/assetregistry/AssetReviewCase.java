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
@Table(name = "asset_review_cases")
class AssetReviewCase extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Column(name = "revision_digest", nullable = false, length = 64, updatable = false)
    private String revisionDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssetReviewState state;

    @Column(name = "policy_version", nullable = false, length = 64, updatable = false)
    private String policyVersion;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AssetReviewCase() {
    }

    AssetReviewCase(AssetRevision revision, String policyVersion, UUID requestedByUserId) {
        super(UUID.randomUUID());
        this.organizationId = revision.getOrganizationId();
        this.assetId = revision.getAssetId();
        this.revisionId = revision.getId();
        this.revisionDigest = revision.getDigest();
        this.state = AssetReviewState.IN_REVIEW;
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
        this.requestedByUserId = Objects.requireNonNull(requestedByUserId, "requestedByUserId");
    }

    void decide(AssetReviewDecisionType decision, Instant decidedAt) {
        if (state != AssetReviewState.IN_REVIEW) {
            throw new AssetConflictException("This review case is already resolved");
        }
        state = switch (decision) {
            case REQUEST_CHANGES -> AssetReviewState.CHANGES_REQUESTED;
            case REJECT -> AssetReviewState.REJECTED;
            case APPROVE -> AssetReviewState.APPROVED;
            case CANCEL -> AssetReviewState.CANCELLED;
        };
        resolvedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getAssetId() {
        return assetId;
    }

    UUID getRevisionId() {
        return revisionId;
    }

    String getRevisionDigest() {
        return revisionDigest;
    }

    AssetReviewState getState() {
        return state;
    }

    String getPolicyVersion() {
        return policyVersion;
    }

    UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    Instant getResolvedAt() {
        return resolvedAt;
    }
}
