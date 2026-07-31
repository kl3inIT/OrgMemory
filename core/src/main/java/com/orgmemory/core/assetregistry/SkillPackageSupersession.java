package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "skill_package_supersessions")
class SkillPackageSupersession extends BaseEntity {

    static final int MAX_ATTEMPTS = 10;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "superseded_reference_value", nullable = false, length = 1024, updatable = false)
    private String supersededReferenceValue;

    @Column(name = "replacement_reference_value", nullable = false, length = 1024, updatable = false)
    private String replacementReferenceValue;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    protected SkillPackageSupersession() {
    }

    SkillPackageSupersession(
            UUID organizationId,
            UUID assetId,
            String supersededReferenceValue,
            String replacementReferenceValue,
            Instant now) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.supersededReferenceValue = reference(
                supersededReferenceValue, "supersededReferenceValue");
        this.replacementReferenceValue = reference(
                replacementReferenceValue, "replacementReferenceValue");
        if (this.supersededReferenceValue.equals(this.replacementReferenceValue)) {
            throw new IllegalArgumentException("Skill package replacement must use a fresh object");
        }
        this.nextAttemptAt = Objects.requireNonNull(now, "now");
    }

    void recordFailure(RuntimeException failure, Instant now) {
        attemptCount++;
        lastErrorCode = bounded(failure.getClass().getSimpleName(), 64);
        lastErrorMessage = "Object storage cleanup failed";
        long delayMinutes = Math.min(1L << Math.min(attemptCount - 1, 6), 60L);
        nextAttemptAt = Objects.requireNonNull(now, "now")
                .plus(delayMinutes, ChronoUnit.MINUTES);
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getAssetId() {
        return assetId;
    }

    String getSupersededReferenceValue() {
        return supersededReferenceValue;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    private static String reference(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > 1024) {
            throw new IllegalArgumentException(field + " is blank or exceeds its limit");
        }
        return normalized;
    }

    private static String bounded(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return "Unspecified storage failure";
        }
        String normalized = value.strip();
        return normalized.substring(0, Math.min(normalized.length(), maximumLength));
    }
}
