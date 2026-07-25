package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "asset_revisions")
class AssetRevision extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequence;

    @Column(nullable = false, length = 256, updatable = false)
    private String title;

    @Column(nullable = false, length = 1024, updatable = false)
    private String summary;

    @Column(nullable = false, length = 32, updatable = false)
    private String classification;

    @Column(name = "schema_version", nullable = false, length = 32, updatable = false)
    private String schemaVersion;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(nullable = false, length = 64, updatable = false)
    private String digest;

    @Column(name = "change_note", nullable = false, length = 1024, updatable = false)
    private String changeNote;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    protected AssetRevision() {
    }

    AssetRevision(
            AssetDraft draft,
            long sequence,
            AssetPayloadDigester.CanonicalAssetPayload canonical,
            String changeNote,
            UUID createdByUserId) {
        super(UUID.randomUUID());
        if (sequence <= 0) {
            throw new IllegalArgumentException("Revision sequence must be positive");
        }
        this.organizationId = draft.getOrganizationId();
        this.assetId = draft.getAssetId();
        this.sequence = sequence;
        AssetPayloadDigester.CanonicalAssetPayload content =
                Objects.requireNonNull(canonical, "canonical");
        this.title = content.title();
        this.summary = content.summary();
        this.classification = content.classification();
        this.schemaVersion = content.schemaVersion();
        this.payload = content.payload();
        this.digest = content.digest();
        this.changeNote = requireText(changeNote, "changeNote");
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getAssetId() {
        return assetId;
    }

    long getSequence() {
        return sequence;
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

    String getDigest() {
        return digest;
    }

    String getChangeNote() {
        return changeNote;
    }

    UUID getCreatedByUserId() {
        return createdByUserId;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
