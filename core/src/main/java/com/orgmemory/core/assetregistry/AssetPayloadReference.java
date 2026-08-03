package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "asset_payload_references")
class AssetPayloadReference extends BaseEntity {

    enum OwnerKind {
        DRAFT,
        REVISION,
        RELEASE
    }

    enum ReferenceKind {
        INLINE,
        BLOB
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32, updatable = false)
    private OwnerKind ownerKind;

    @Column(name = "draft_id", updatable = false)
    private UUID draftId;

    @Column(name = "revision_id", updatable = false)
    private UUID revisionId;

    @Column(name = "release_id", updatable = false)
    private UUID releaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_kind", nullable = false, length = 32, updatable = false)
    private ReferenceKind referenceKind;

    @Column(name = "reference_value", nullable = false, length = 1024, updatable = false)
    private String referenceValue;

    // INLINE references may omit transport metadata; BLOB references may not.
    @Column(length = 64, updatable = false)
    private String digest;

    @Column(name = "media_type", length = 128, updatable = false)
    private String mediaType;

    @Column(name = "content_length", updatable = false)
    private Long contentLength;

    protected AssetPayloadReference() {
    }

    private AssetPayloadReference(
            UUID organizationId,
            OwnerKind ownerKind,
            UUID draftId,
            UUID revisionId,
            UUID releaseId,
            String referenceValue,
            String digest,
            String mediaType,
            long contentLength) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.ownerKind = Objects.requireNonNull(ownerKind, "ownerKind");
        this.draftId = draftId;
        this.revisionId = revisionId;
        this.releaseId = releaseId;
        this.referenceKind = ReferenceKind.BLOB;
        this.referenceValue = required(referenceValue, "referenceValue", 1024);
        this.digest = required(digest, "digest", 64);
        if (!this.digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Payload reference digest must be SHA-256");
        }
        this.mediaType = required(mediaType, "mediaType", 128);
        if (contentLength <= 0) {
            throw new IllegalArgumentException("Payload reference content length must be positive");
        }
        this.contentLength = contentLength;
    }

    static AssetPayloadReference forDraft(
            AssetDraft draft,
            SkillPackageStoragePort.StoredSkillPackage artifact) {
        return new AssetPayloadReference(
                draft.getOrganizationId(),
                OwnerKind.DRAFT,
                draft.getId(),
                null,
                null,
                artifact.objectKey(),
                artifact.sha256(),
                artifact.mediaType(),
                artifact.contentLength());
    }

    static AssetPayloadReference forRevision(
            AssetRevision revision, AssetPayloadReference draftReference) {
        return new AssetPayloadReference(
                revision.getOrganizationId(),
                OwnerKind.REVISION,
                null,
                revision.getId(),
                null,
                draftReference.referenceValue,
                draftReference.digest,
                draftReference.mediaType,
                draftReference.contentLength);
    }

    static AssetPayloadReference forRelease(
            AssetRelease release, AssetPayloadReference revisionReference) {
        return new AssetPayloadReference(
                release.getOrganizationId(),
                OwnerKind.RELEASE,
                null,
                null,
                release.getId(),
                revisionReference.referenceValue,
                revisionReference.digest,
                revisionReference.mediaType,
                revisionReference.contentLength);
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getDraftId() {
        return draftId;
    }

    UUID getRevisionId() {
        return revisionId;
    }

    UUID getReleaseId() {
        return releaseId;
    }

    String getReferenceValue() {
        return referenceValue;
    }

    boolean isBlobReference() {
        return referenceKind == ReferenceKind.BLOB;
    }

    String getDigest() {
        return digest;
    }

    String getMediaType() {
        return mediaType;
    }

    Long getContentLength() {
        return contentLength;
    }

    private static String required(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds its limit");
        }
        return normalized;
    }
}
