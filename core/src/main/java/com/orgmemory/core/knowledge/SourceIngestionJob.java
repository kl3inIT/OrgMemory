package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_ingestion_jobs")
class SourceIngestionJob extends BaseEntity {

    static final String TYPE = "PROCESS_SOURCE_REVISION";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_revision_id", nullable = false, updatable = false)
    private UUID sourceRevisionId;

    @Column(name = "job_type", nullable = false, length = 64, updatable = false)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SourceIngestionJobStatus status;

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

    protected SourceIngestionJob() {
    }

    SourceIngestionJob(UUID organizationId, UUID sourceRevisionId, int maxAttempts, Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceRevisionId = sourceRevisionId;
        this.jobType = TYPE;
        this.status = SourceIngestionJobStatus.PENDING;
        this.availableAt = now;
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts;
    }

    void claim(String workerId, Instant now, Duration leaseDuration) {
        this.status = SourceIngestionJobStatus.PROCESSING;
        this.leaseOwner = workerId;
        this.leaseUntil = now.plus(leaseDuration);
        this.attemptCount++;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    boolean isClaimedBy(String workerId) {
        return status == SourceIngestionJobStatus.PROCESSING && workerId.equals(leaseOwner);
    }

    void refreshLease(Instant now, Duration leaseDuration) {
        this.leaseUntil = now.plus(leaseDuration);
    }

    void succeed() {
        this.status = SourceIngestionJobStatus.SUCCEEDED;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    boolean retry(String code, String message, Instant nextAttempt) {
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.lastErrorCode = code;
        this.lastErrorMessage = SourceFailureMessage.truncate(message);
        if (attemptCount >= maxAttempts) {
            this.status = SourceIngestionJobStatus.FAILED;
            return false;
        }
        this.status = SourceIngestionJobStatus.PENDING;
        this.availableAt = nextAttempt;
        return true;
    }

    void failPermanently(String code, String message) {
        this.status = SourceIngestionJobStatus.FAILED;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.lastErrorCode = code;
        this.lastErrorMessage = SourceFailureMessage.truncate(message);
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getSourceRevisionId() {
        return sourceRevisionId;
    }

    int getAttemptCount() {
        return attemptCount;
    }
}
