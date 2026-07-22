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
 * An external identity observed from a source system. Observation records what the
 * source reported; it grants nothing on its own. Authorization flows only through a
 * verified {@link SourcePrincipalMapping}.
 */
@Entity
@Table(name = "source_principals")
class SourcePrincipal extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "external_key", nullable = false, length = 512, updatable = false)
    private String externalKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private SourcePrincipalKind kind;

    @Column(name = "observed_email", length = 320)
    private String observedEmail;

    @Column(name = "observed_display_name", length = 256)
    private String observedDisplayName;

    @Column(name = "sso_verified", nullable = false)
    private boolean ssoVerified;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected SourcePrincipal() {
    }

    SourcePrincipal(
            UUID id,
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalKey,
            SourcePrincipalKind kind,
            String observedEmail,
            String observedDisplayName,
            boolean ssoVerified,
            Instant lastSeenAt) {
        super(id);
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.externalKey = externalKey;
        this.kind = kind;
        this.observedEmail = observedEmail;
        this.observedDisplayName = observedDisplayName;
        this.ssoVerified = ssoVerified;
        this.lastSeenAt = lastSeenAt;
    }

    void observe(String observedEmail, String observedDisplayName, boolean ssoVerified, Instant lastSeenAt) {
        this.observedEmail = observedEmail;
        this.observedDisplayName = observedDisplayName;
        this.ssoVerified = ssoVerified;
        this.lastSeenAt = lastSeenAt;
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

    String getExternalKey() {
        return externalKey;
    }

    SourcePrincipalKind getKind() {
        return kind;
    }

    String getObservedEmail() {
        return observedEmail;
    }

    String getObservedDisplayName() {
        return observedDisplayName;
    }

    boolean isSsoVerified() {
        return ssoVerified;
    }

    Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
