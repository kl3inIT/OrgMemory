package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "asset_releases")
class AssetRelease extends BaseEntity {

    private static final Pattern VERSION_LABEL =
            Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "revision_id", nullable = false, updatable = false)
    private UUID revisionId;

    @Column(name = "release_sequence", nullable = false, updatable = false)
    private long sequence;

    @Column(name = "version_label", nullable = false, length = 64, updatable = false)
    private String versionLabel;

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

    @Column(name = "released_by_user_id", nullable = false, updatable = false)
    private UUID releasedByUserId;

    @Column(name = "released_at", nullable = false, updatable = false)
    private Instant releasedAt;

    protected AssetRelease() {
    }

    AssetRelease(
            AssetRevision revision,
            long sequence,
            String versionLabel,
            UUID releasedByUserId,
            Instant releasedAt) {
        super(UUID.randomUUID());
        if (sequence <= 0) {
            throw new IllegalArgumentException("Release sequence must be positive");
        }
        this.organizationId = revision.getOrganizationId();
        this.assetId = revision.getAssetId();
        this.revisionId = revision.getId();
        this.sequence = sequence;
        this.versionLabel = versionLabel(versionLabel);
        this.title = revision.getTitle();
        this.summary = revision.getSummary();
        this.classification = revision.getClassification();
        this.schemaVersion = revision.getSchemaVersion();
        this.payload = revision.getPayload();
        this.digest = revision.getDigest();
        this.releasedByUserId = Objects.requireNonNull(releasedByUserId, "releasedByUserId");
        this.releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getAssetId() {
        return assetId;
    }

    UUID getRevisionId() {
        return revisionId;
    }

    long getSequence() {
        return sequence;
    }

    String getVersionLabel() {
        return versionLabel;
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

    UUID getReleasedByUserId() {
        return releasedByUserId;
    }

    Instant getReleasedAt() {
        return releasedAt;
    }

    private static String versionLabel(String value) {
        String normalized = Objects.requireNonNull(value, "versionLabel")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!VERSION_LABEL.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "versionLabel must contain lowercase words separated by '.', '_', or '-'");
        }
        return normalized;
    }
}
