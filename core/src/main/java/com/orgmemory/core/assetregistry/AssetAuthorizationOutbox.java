package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "asset_authorization_outbox")
class AssetAuthorizationOutbox extends BaseEntity {

    static final int MAX_ATTEMPTS = 8;
    private static final long MAX_BACKOFF_SECONDS = 900;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "role_assignment_id", updatable = false)
    private UUID roleAssignmentId;

    @Column(name = "tuple_user", nullable = false, length = 256, updatable = false)
    private String tupleUser;

    @Column(name = "tuple_relation", nullable = false, length = 64, updatable = false)
    private String tupleRelation;

    @Column(name = "tuple_object", nullable = false, length = 256, updatable = false)
    private String tupleObject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssetAuthorizationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "authorization_model_id")
    private String authorizationModelId;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    @Column(name = "applied_at")
    private Instant appliedAt;

    protected AssetAuthorizationOutbox() {
    }

    AssetAuthorizationOutbox(
            UUID organizationId,
            UUID assetId,
            UUID roleAssignmentId,
            RelationshipTuple tuple) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.roleAssignmentId = roleAssignmentId;
        RelationshipTuple relationship = Objects.requireNonNull(tuple, "tuple");
        this.tupleUser = relationship.user();
        this.tupleRelation = relationship.relation();
        this.tupleObject = relationship.object();
        this.status = AssetAuthorizationStatus.PENDING;
        this.nextAttemptAt = Instant.EPOCH;
    }

    void claim(UUID token, Instant now, Instant leasedUntil) {
        if (!isClaimableAt(now)) {
            throw new AssetConflictException(
                    "Asset authorization work is already claimed or not due");
        }
        claimToken = Objects.requireNonNull(token, "token");
        leaseUntil = Objects.requireNonNull(leasedUntil, "leasedUntil");
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("Asset authorization lease must be in the future");
        }
        status = AssetAuthorizationStatus.IN_FLIGHT;
        attemptCount++;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    void recordFailure(UUID token, String code, String message, Instant failedAt) {
        requireClaim(token);
        lastErrorCode = truncate(code, 64);
        lastErrorMessage = truncate(message, 512);
        claimToken = null;
        leaseUntil = null;
        if (attemptCount >= MAX_ATTEMPTS) {
            status = AssetAuthorizationStatus.DEAD_LETTER;
            nextAttemptAt = failedAt;
            return;
        }
        status = AssetAuthorizationStatus.PENDING;
        nextAttemptAt = failedAt.plusSeconds(backoffSeconds());
    }

    void markApplied(UUID token, String modelId, Instant timestamp) {
        requireClaim(token);
        status = AssetAuthorizationStatus.APPLIED;
        authorizationModelId = Objects.requireNonNull(modelId, "modelId");
        appliedAt = Objects.requireNonNull(timestamp, "timestamp");
        claimToken = null;
        leaseUntil = null;
        nextAttemptAt = timestamp;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    RelationshipTuple tuple() {
        return RelationshipTuple.of(tupleUser, tupleRelation, tupleObject);
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getAssetId() {
        return assetId;
    }

    UUID getRoleAssignmentId() {
        return roleAssignmentId;
    }

    AssetAuthorizationStatus getStatus() {
        return status;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    private boolean isClaimableAt(Instant now) {
        return (status == AssetAuthorizationStatus.PENDING
                        && !nextAttemptAt.isAfter(now))
                || (status == AssetAuthorizationStatus.IN_FLIGHT
                        && leaseUntil != null
                        && !leaseUntil.isAfter(now));
    }

    private void requireClaim(UUID token) {
        if (status != AssetAuthorizationStatus.IN_FLIGHT
                || claimToken == null
                || !claimToken.equals(token)) {
            throw new AssetConflictException(
                    "Asset authorization claim is stale or no longer owned");
        }
    }

    private long backoffSeconds() {
        int exponent = Math.max(0, attemptCount - 1);
        long seconds = 5L << Math.min(exponent, 8);
        return Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private static String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "UNSPECIFIED" : value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
