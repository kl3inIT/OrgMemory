package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "evidence_blobs")
class EvidenceBlob extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "object_key", nullable = false, length = 1024, updatable = false)
    private String objectKey;

    @Column(name = "media_type", nullable = false, updatable = false)
    private String mediaType;

    @Column(name = "content_length", nullable = false, updatable = false)
    private long contentLength;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Column(updatable = false)
    private String etag;

    @Column(name = "storage_version", updatable = false)
    private String storageVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 32)
    private EvidenceScanStatus scanStatus;

    protected EvidenceBlob() {
    }

    EvidenceBlob(UUID id, UUID organizationId, StoredObject stored) {
        super(id);
        this.organizationId = organizationId;
        this.objectKey = stored.key().value();
        this.mediaType = stored.mediaType();
        this.contentLength = stored.contentLength();
        this.contentSha256 = stored.sha256();
        this.etag = stored.etag();
        this.storageVersion = stored.storageVersion();
        this.scanStatus = EvidenceScanStatus.PENDING;
    }

    void markValidated() {
        this.scanStatus = EvidenceScanStatus.BASIC_VALIDATED;
    }

    void reject() {
        this.scanStatus = EvidenceScanStatus.REJECTED;
    }

    String getObjectKey() {
        return objectKey;
    }

    String getMediaType() {
        return mediaType;
    }

    long getContentLength() {
        return contentLength;
    }

    String getContentSha256() {
        return contentSha256;
    }

    EvidenceScanStatus getScanStatus() {
        return scanStatus;
    }
}
