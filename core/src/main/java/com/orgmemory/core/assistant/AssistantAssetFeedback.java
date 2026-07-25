package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_asset_feedback")
class AssistantAssetFeedback extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "release_digest", nullable = false, length = 64, updatable = false)
    private String releaseDigest;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 24, updatable = false)
    private String type;

    @Column(nullable = false, length = 2000, updatable = false)
    private String comment;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected AssistantAssetFeedback() {
    }

    AssistantAssetFeedback(
            UUID organizationId,
            UUID assetId,
            UUID releaseId,
            String releaseDigest,
            UUID actorUserId,
            AssistantFeedbackType type,
            String comment,
            Instant submittedAt) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseDigest = Objects.requireNonNull(releaseDigest, "releaseDigest");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.type = Objects.requireNonNull(type, "type").name();
        this.comment = requiredComment(comment);
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    private static String requiredComment(String value) {
        String normalized = Objects.requireNonNull(value, "comment").strip();
        if (normalized.isEmpty() || normalized.length() > 2000) {
            throw new IllegalArgumentException(
                    "Feedback comment is blank or exceeds 2000 characters");
        }
        return normalized;
    }
}
