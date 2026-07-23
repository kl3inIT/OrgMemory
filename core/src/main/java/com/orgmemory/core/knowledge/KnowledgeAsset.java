package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable governed knowledge identity. OpenFGA relationships target this ID;
 * immutable content and security provenance live in {@link KnowledgeAssetVersion}.
 */
@Entity
@Table(name = "knowledge_assets")
public class KnowledgeAsset extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Column(name = "source_object_id", updatable = false)
    private UUID sourceObjectId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected KnowledgeAsset() {
    }

    KnowledgeAsset(UUID organizationId, UUID knowledgeSpaceId, UUID sourceObjectId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        this.sourceObjectId = sourceObjectId;
    }

    void useVersion(UUID versionId) {
        if (archivedAt != null) {
            throw new IllegalStateException("An archived knowledge asset cannot publish a version");
        }
        currentVersionId = Objects.requireNonNull(versionId, "versionId");
    }

    void archive(Instant timestamp) {
        archivedAt = Objects.requireNonNull(timestamp, "timestamp");
        currentVersionId = null;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public UUID getSourceObjectId() {
        return sourceObjectId;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
