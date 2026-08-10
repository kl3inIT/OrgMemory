package com.orgmemory.core.assetregistry.kernel;

import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.authorization.PrincipalRef;
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
@Table(name = "asset_role_assignments")
class AssetRoleAssignment extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "principal_type", nullable = false, length = 32, updatable = false)
    private String principalType;

    @Column(name = "principal_id", nullable = false, length = 128, updatable = false)
    private String principalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AssetRole role;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "assigned_by_user_id", nullable = false, updatable = false)
    private UUID assignedByUserId;

    @Column(name = "projected_at")
    private Instant projectedAt;

    protected AssetRoleAssignment() {
    }

    AssetRoleAssignment(
            UUID organizationId,
            UUID assetId,
            PrincipalRef principal,
            AssetRole role,
            UUID assignedByUserId,
            Instant validFrom) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        PrincipalRef subject = Objects.requireNonNull(principal, "principal");
        if (!subject.type().equals("user")
                && !subject.type().equals("group")
                && !subject.type().equals("organization")) {
            throw new IllegalArgumentException(
                    "Asset roles may be assigned only to users, groups, or organizations");
        }
        this.principalType = subject.type();
        this.principalId = subject.id();
        this.role = Objects.requireNonNull(role, "role");
        this.assignedByUserId = Objects.requireNonNull(assignedByUserId, "assignedByUserId");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
    }

    void markProjected(Instant timestamp) {
        projectedAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    void expire(Instant timestamp) {
        Instant expiry = Objects.requireNonNull(timestamp, "timestamp");
        if (!expiry.isAfter(validFrom)) {
            throw new IllegalArgumentException("Asset role expiry must follow its assignment");
        }
        if (validUntil != null) {
            throw new AssetConflictException("Asset role assignment is already inactive");
        }
        validUntil = expiry;
    }

    String getPrincipalType() {
        return principalType;
    }

    String getPrincipalId() {
        return principalId;
    }

    AssetRole getRole() {
        return role;
    }

    Instant getValidFrom() {
        return validFrom;
    }

    Instant getValidUntil() {
        return validUntil;
    }

    UUID getAssignedByUserId() {
        return assignedByUserId;
    }

    Instant getProjectedAt() {
        return projectedAt;
    }
}
