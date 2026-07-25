package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "asset_drafts")
class AssetDraft extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, length = 1024)
    private String summary;

    @Column(nullable = false, length = 32)
    private String classification;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(nullable = false)
    private String payload;

    @Column(name = "edited_by_user_id", nullable = false)
    private UUID editedByUserId;

    protected AssetDraft() {
    }

    AssetDraft(
            UUID organizationId,
            UUID assetId,
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload,
            UUID editedByUserId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        update(title, summary, classification, schemaVersion, payload, editedByUserId);
    }

    void update(
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload,
            UUID editedByUserId) {
        this.title = text(title, "title", 256);
        this.summary = text(summary, "summary", 1024);
        this.classification = text(classification, "classification", 32);
        this.schemaVersion = text(schemaVersion, "schemaVersion", 32);
        this.payload = text(payload, "payload", Integer.MAX_VALUE);
        this.editedByUserId = Objects.requireNonNull(editedByUserId, "editedByUserId");
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getAssetId() {
        return assetId;
    }

    String getTitle() {
        return title;
    }

    String getSummary() {
        return summary;
    }

    String getClassification() {
        return classification;
    }

    String getSchemaVersion() {
        return schemaVersion;
    }

    String getPayload() {
        return payload;
    }

    UUID getEditedByUserId() {
        return editedByUserId;
    }

    private static String text(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must contain between 1 and " + maxLength + " characters");
        }
        return normalized;
    }
}
