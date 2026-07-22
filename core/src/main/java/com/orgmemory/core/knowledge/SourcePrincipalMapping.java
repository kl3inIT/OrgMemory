package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A verified link from a {@code SOURCE_USER} principal to an internal user. At most one
 * mapping is {@code ACTIVE} per principal (enforced by a partial unique index). Revocation
 * closes the link without deleting the audit trail.
 */
@Entity
@Table(name = "source_principal_mappings")
class SourcePrincipalMapping extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_principal_id", nullable = false, updatable = false)
    private UUID sourcePrincipalId;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private UUID appUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private SourcePrincipalMappingMethod method;

    @Column(nullable = false, length = 512, updatable = false)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SourcePrincipalMappingStatus status;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SourcePrincipalMapping() {
    }

    SourcePrincipalMapping(
            UUID id,
            UUID organizationId,
            UUID sourcePrincipalId,
            UUID appUserId,
            SourcePrincipalMappingMethod method,
            String evidence,
            Instant verifiedAt) {
        super(id);
        this.organizationId = organizationId;
        this.sourcePrincipalId = sourcePrincipalId;
        this.appUserId = appUserId;
        this.method = method;
        this.evidence = evidence;
        this.status = SourcePrincipalMappingStatus.ACTIVE;
        this.verifiedAt = verifiedAt;
    }

    void revoke(Instant revokedAt) {
        this.status = SourcePrincipalMappingStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    boolean isActive() {
        return status == SourcePrincipalMappingStatus.ACTIVE;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getSourcePrincipalId() {
        return sourcePrincipalId;
    }

    UUID getAppUserId() {
        return appUserId;
    }

    SourcePrincipalMappingMethod getMethod() {
        return method;
    }

    String getEvidence() {
        return evidence;
    }

    SourcePrincipalMappingStatus getStatus() {
        return status;
    }

    Instant getVerifiedAt() {
        return verifiedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
