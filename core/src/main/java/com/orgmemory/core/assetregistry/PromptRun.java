package com.orgmemory.core.assetregistry;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "prompt_runs")
class PromptRun extends BaseEntity {

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

    @Column(name = "gateway_id", nullable = false, length = 64, updatable = false)
    private String gatewayId;

    @Column(name = "model_id", nullable = false, length = 256, updatable = false)
    private String modelId;

    @Column(name = "input_shape_digest", nullable = false, length = 64, updatable = false)
    private String inputShapeDigest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citation_refs", nullable = false, updatable = false)
    private String citationRefs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromptRunStatus status;

    @Column(name = "duration_millis")
    private Long durationMillis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanitized_outcome")
    private String sanitizedOutcome;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PromptRun() {
    }

    PromptRun(
            UUID organizationId,
            UUID assetId,
            UUID releaseId,
            String releaseDigest,
            UUID actorUserId,
            AiRoute route,
            String inputShapeDigest,
            String citationRefs,
            Instant startedAt) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.releaseDigest = required(releaseDigest, "releaseDigest", 64);
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        AiRoute pinnedRoute = Objects.requireNonNull(route, "route");
        this.gatewayId = required(pinnedRoute.gatewayId(), "gatewayId", 64);
        this.modelId = required(pinnedRoute.modelId(), "modelId", 256);
        this.inputShapeDigest = required(inputShapeDigest, "inputShapeDigest", 64);
        this.citationRefs = required(citationRefs, "citationRefs", 64_000);
        this.status = PromptRunStatus.RUNNING;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    void succeed(String outcome, Instant completedAt) {
        requireRunning();
        this.status = PromptRunStatus.SUCCEEDED;
        this.sanitizedOutcome = required(outcome, "outcome", 64_000);
        finish(completedAt);
    }

    void fail(String errorCode, Instant completedAt) {
        requireRunning();
        this.status = PromptRunStatus.FAILED;
        this.errorCode = required(errorCode, "errorCode", 64);
        finish(completedAt);
    }

    private void finish(Instant timestamp) {
        completedAt = Objects.requireNonNull(timestamp, "completedAt");
        durationMillis = Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli());
    }

    private void requireRunning() {
        if (status != PromptRunStatus.RUNNING) {
            throw new IllegalStateException("Prompt run is already complete");
        }
    }

    private static String required(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds maximum length");
        }
        return normalized;
    }
}
