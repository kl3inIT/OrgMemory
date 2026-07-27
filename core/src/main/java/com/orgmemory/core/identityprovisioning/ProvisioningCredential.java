package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authentication metadata only. The bearer value is deliberately absent; the
 * verifier is produced by the keyed hashing boundary before this aggregate is
 * called.
 */
@Entity
@Table(name = "provisioning_credentials")
class ProvisioningCredential extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "public_token_id", nullable = false, length = 64, updatable = false)
    private String publicTokenId;

    @Column(name = "verifier_digest", nullable = false, length = 43, updatable = false)
    private String verifierDigest;

    @Column(name = "verifier_key_version", nullable = false, updatable = false)
    private int verifierKeyVersion;

    @Column(name = "users_scope", nullable = false, updatable = false)
    private boolean usersScope;

    @Column(name = "groups_scope", nullable = false, updatable = false)
    private boolean groupsScope;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "overlap_ends_at")
    private Instant overlapEndsAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    protected ProvisioningCredential() {
    }

    ProvisioningCredential(
            UUID organizationId,
            UUID connectionId,
            String publicTokenId,
            String verifierDigest,
            int verifierKeyVersion,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt,
            UUID createdByUserId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.publicTokenId = requireText(publicTokenId, "publicTokenId");
        this.verifierDigest = requireVerifierDigest(verifierDigest);
        if (verifierKeyVersion <= 0) {
            throw new IllegalArgumentException("verifierKeyVersion must be positive");
        }
        this.verifierKeyVersion = verifierKeyVersion;
        this.usersScope = usersScope;
        this.groupsScope = groupsScope;
        this.expiresAt = expiresAt;
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
    }

    String getPublicTokenId() {
        return publicTokenId;
    }

    @Override
    public String toString() {
        return "ProvisioningCredential[id=" + getId()
                + ", connectionId=" + connectionId
                + ", publicTokenId=" + publicTokenId
                + ", verifierKeyVersion=" + verifierKeyVersion + "]";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String requireVerifierDigest(String value) {
        String digest = requireText(value, "verifierDigest");
        if (!digest.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException(
                    "verifierDigest must be an unpadded base64url SHA-256 digest");
        }
        return digest;
    }
}
