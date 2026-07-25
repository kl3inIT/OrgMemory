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
@Table(name = "asset_review_decisions")
class AssetReviewDecision extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "review_case_id", nullable = false, updatable = false)
    private UUID reviewCaseId;

    @Column(name = "revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Column(name = "revision_digest", nullable = false, length = 64, updatable = false)
    private String revisionDigest;

    @Column(name = "reviewer_user_id", nullable = false, updatable = false)
    private UUID reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AssetReviewDecisionType decision;

    @Column(nullable = false, length = 2048, updatable = false)
    private String comment;

    @Column(name = "policy_version", nullable = false, length = 64, updatable = false)
    private String policyVersion;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected AssetReviewDecision() {
    }

    AssetReviewDecision(
            AssetReviewCase review,
            UUID reviewerUserId,
            AssetReviewDecisionType decision,
            String comment,
            Instant decidedAt) {
        super(UUID.randomUUID());
        this.organizationId = review.getOrganizationId();
        this.assetId = review.getAssetId();
        this.reviewCaseId = review.getId();
        this.revisionId = review.getRevisionId();
        this.revisionDigest = review.getRevisionDigest();
        this.reviewerUserId = Objects.requireNonNull(reviewerUserId, "reviewerUserId");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.comment = comment == null ? "" : comment.trim();
        this.policyVersion = review.getPolicyVersion();
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }

    UUID getReviewerUserId() {
        return reviewerUserId;
    }

    AssetReviewDecisionType getDecision() {
        return decision;
    }

    String getComment() {
        return comment;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }
}
