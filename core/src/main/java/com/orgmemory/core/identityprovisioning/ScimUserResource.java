package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The supported SCIM User projection. There is intentionally no JSON/raw
 * request column: unsupported attributes are rejected at the protocol boundary.
 */
@Entity
@Table(name = "scim_user_resources")
class ScimUserResource extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "app_user_id")
    private UUID appUserId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "normalized_user_name", nullable = false, length = 320)
    private String normalizedUserName;

    @Column(name = "normalized_email", length = 320)
    private String normalizedEmail;

    @Column(name = "workforce_key")
    private String workforceKey;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "given_name")
    private String givenName;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "directory_active", nullable = false)
    private boolean directoryActive;

    @Column(name = "tombstoned_at")
    private Instant tombstonedAt;

    protected ScimUserResource() {
    }

    ScimUserResource(
            UUID organizationId,
            UUID connectionId,
            UUID appUserId,
            String externalId,
            String userName,
            String email,
            String workforceKey,
            String displayName,
            String givenName,
            String familyName,
            boolean directoryActive) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.appUserId = appUserId;
        this.externalId = optionalText(externalId);
        this.normalizedUserName = normalizeRequired(userName, "userName");
        this.normalizedEmail = normalizeOptional(email);
        this.workforceKey = optionalText(workforceKey);
        this.displayName = optionalText(displayName);
        this.givenName = optionalText(givenName);
        this.familyName = optionalText(familyName);
        this.directoryActive = directoryActive;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getConnectionId() {
        return connectionId;
    }

    UUID getAppUserId() {
        return appUserId;
    }

    String getExternalId() {
        return externalId;
    }

    String getNormalizedUserName() {
        return normalizedUserName;
    }

    String getNormalizedEmail() {
        return normalizedEmail;
    }

    boolean isDirectoryActive() {
        return directoryActive;
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        String text = optionalText(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
