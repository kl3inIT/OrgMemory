package com.orgmemory.core.assetregistry;

import com.orgmemory.core.authorization.RelationshipTuple;
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
@Table(name = "asset_authorization_outbox")
class AssetAuthorizationOutbox extends BaseEntity {

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
    }

    void startAttempt() {
        if (status == AssetAuthorizationStatus.PENDING) {
            attemptCount++;
            lastErrorCode = null;
            lastErrorMessage = null;
        }
    }

    void recordFailure(String code, String message) {
        if (status == AssetAuthorizationStatus.PENDING) {
            lastErrorCode = truncate(code, 64);
            lastErrorMessage = truncate(message, 512);
        }
    }

    void markApplied(String modelId, Instant timestamp) {
        status = AssetAuthorizationStatus.APPLIED;
        authorizationModelId = Objects.requireNonNull(modelId, "modelId");
        appliedAt = Objects.requireNonNull(timestamp, "timestamp");
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

    String getAuthorizationModelId() {
        return authorizationModelId;
    }

    private static String truncate(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "UNSPECIFIED" : value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
