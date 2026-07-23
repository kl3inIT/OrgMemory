package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "graph_index_jobs")
class GraphIndexJob extends BaseEntity {

    static final String TYPE = "INDEX_KNOWLEDGE_ASSET_VERSION";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_asset_id", nullable = false, updatable = false)
    private UUID knowledgeAssetId;

    @Column(name = "knowledge_asset_version_id", nullable = false, updatable = false)
    private UUID knowledgeAssetVersionId;

    @Column(name = "source_revision_id", nullable = false, updatable = false)
    private UUID sourceRevisionId;

    @Column(name = "projection_generation", nullable = false, updatable = false)
    private long projectionGeneration;

    @Column(name = "job_type", nullable = false, length = 64, updatable = false)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GraphIndexJobStatus status;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected GraphIndexJob() {
    }

    GraphIndexJob(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            UUID sourceRevisionId,
            long projectionGeneration,
            int maxAttempts,
            Instant now) {
        super(UUID.randomUUID());
        if (projectionGeneration <= 0) {
            throw new IllegalArgumentException("projectionGeneration must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.knowledgeAssetId = Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        this.knowledgeAssetVersionId =
                Objects.requireNonNull(knowledgeAssetVersionId, "knowledgeAssetVersionId");
        this.sourceRevisionId = Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        this.projectionGeneration = projectionGeneration;
        this.jobType = TYPE;
        this.status = GraphIndexJobStatus.PENDING;
        this.availableAt = Objects.requireNonNull(now, "now");
        this.maxAttempts = maxAttempts;
    }

    void claim(String workerId, Instant now, Duration leaseDuration) {
        status = GraphIndexJobStatus.PROCESSING;
        leaseOwner = workerId;
        leaseUntil = now.plus(leaseDuration);
        attemptCount++;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    boolean isClaimedBy(String workerId) {
        return status == GraphIndexJobStatus.PROCESSING && workerId.equals(leaseOwner);
    }

    boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }

    void refreshLease(Instant now, Duration leaseDuration) {
        leaseUntil = now.plus(leaseDuration);
    }

    void succeed(Instant now) {
        status = GraphIndexJobStatus.SUCCEEDED;
        leaseOwner = null;
        leaseUntil = null;
        lastErrorCode = null;
        lastErrorMessage = null;
        completedAt = now;
    }

    void supersede(Instant now) {
        status = GraphIndexJobStatus.SUPERSEDED;
        leaseOwner = null;
        leaseUntil = null;
        lastErrorCode = "VERSION_SUPERSEDED";
        lastErrorMessage = "The Knowledge Asset no longer points at this version";
        completedAt = now;
    }

    void failExpiredLease(Instant now) {
        status = GraphIndexJobStatus.FAILED;
        leaseOwner = null;
        leaseUntil = null;
        lastErrorCode = "LEASE_EXPIRED";
        lastErrorMessage = "The final graph indexing attempt lost its worker lease";
        completedAt = now;
    }

    boolean retry(String code, String message, Instant now, Instant nextAttempt) {
        leaseOwner = null;
        leaseUntil = null;
        lastErrorCode = code;
        lastErrorMessage = SourceFailureMessage.truncate(message);
        if (attemptCount >= maxAttempts) {
            status = GraphIndexJobStatus.FAILED;
            completedAt = now;
            return false;
        }
        status = GraphIndexJobStatus.PENDING;
        availableAt = nextAttempt;
        return true;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getKnowledgeAssetId() {
        return knowledgeAssetId;
    }

    UUID getKnowledgeAssetVersionId() {
        return knowledgeAssetVersionId;
    }

    UUID getSourceRevisionId() {
        return sourceRevisionId;
    }

    long getProjectionGeneration() {
        return projectionGeneration;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    GraphIndexJobStatus getStatus() {
        return status;
    }

    Instant getLeaseUntil() {
        return leaseUntil;
    }
}
