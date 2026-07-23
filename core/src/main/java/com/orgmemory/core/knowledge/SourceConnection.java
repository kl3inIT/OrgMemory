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
 * An administrator's standing decision about one source connection. Connections
 * themselves are discovered from observed principals; a row exists here only once
 * somebody has ruled on how much its identities may be trusted.
 */
@Entity
@Table(name = "source_connections")
class SourceConnection extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_trust", nullable = false, length = 32)
    private SourceIdentityTrust identityTrust;

    @Column(name = "trust_decided_by_user_id")
    private UUID trustDecidedByUserId;

    @Column(name = "trust_decided_at")
    private Instant trustDecidedAt;

    protected SourceConnection() {
    }

    SourceConnection(UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.identityTrust = SourceIdentityTrust.UNTRUSTED;
    }

    void decideTrust(SourceIdentityTrust identityTrust, UUID decidedByUserId, Instant decidedAt) {
        this.identityTrust = identityTrust;
        if (identityTrust == SourceIdentityTrust.UNTRUSTED) {
            this.trustDecidedByUserId = null;
            this.trustDecidedAt = null;
            return;
        }
        this.trustDecidedByUserId = decidedByUserId;
        this.trustDecidedAt = decidedAt;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getSourceSystem() {
        return sourceSystem;
    }

    String getSourceConnectionKey() {
        return sourceConnectionKey;
    }

    SourceIdentityTrust getIdentityTrust() {
        return identityTrust;
    }

    UUID getTrustDecidedByUserId() {
        return trustDecidedByUserId;
    }

    Instant getTrustDecidedAt() {
        return trustDecidedAt;
    }
}
