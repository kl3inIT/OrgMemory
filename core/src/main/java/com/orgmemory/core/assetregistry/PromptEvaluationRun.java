package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "prompt_evaluation_runs")
class PromptEvaluationRun extends BaseEntity {

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

    @Column(name = "passed_cases", nullable = false, updatable = false)
    private int passedCases;

    @Column(name = "total_cases", nullable = false, updatable = false)
    private int totalCases;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String details;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    protected PromptEvaluationRun() {
    }

    PromptEvaluationRun(
            UUID organizationId,
            UUID assetId,
            UUID releaseId,
            String releaseDigest,
            UUID actorUserId,
            int passedCases,
            int totalCases,
            String details,
            Instant evaluatedAt) {
        super(UUID.randomUUID());
        if (totalCases <= 0 || passedCases < 0 || passedCases > totalCases) {
            throw new IllegalArgumentException("Prompt evaluation counts are invalid");
        }
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseDigest = Objects.requireNonNull(releaseDigest, "releaseDigest");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.passedCases = passedCases;
        this.totalCases = totalCases;
        this.details = Objects.requireNonNull(details, "details");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }
}
