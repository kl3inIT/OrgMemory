package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** Append-only provenance linking a knowledge version to contributing evidence. */
@Entity
@Table(name = "knowledge_asset_evidence_links")
class KnowledgeAssetEvidenceLink extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_asset_version_id", nullable = false, updatable = false)
    private UUID knowledgeAssetVersionId;

    @Column(name = "source_revision_id", nullable = false, updatable = false)
    private UUID sourceRevisionId;

    @Column(name = "source_acl_snapshot_id", nullable = false, updatable = false)
    private UUID sourceAclSnapshotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_role", nullable = false, length = 32, updatable = false)
    private KnowledgeAssetEvidenceRole evidenceRole;

    @Column(name = "span_start", updatable = false)
    private Integer spanStart;

    @Column(name = "span_end", updatable = false)
    private Integer spanEnd;

    protected KnowledgeAssetEvidenceLink() {
    }

    static KnowledgeAssetEvidenceLink primary(
            UUID organizationId,
            UUID knowledgeAssetVersionId,
            UUID sourceRevisionId,
            UUID sourceAclSnapshotId) {
        return new KnowledgeAssetEvidenceLink(
                organizationId,
                knowledgeAssetVersionId,
                sourceRevisionId,
                sourceAclSnapshotId,
                KnowledgeAssetEvidenceRole.PRIMARY,
                null,
                null);
    }

    private KnowledgeAssetEvidenceLink(
            UUID organizationId,
            UUID knowledgeAssetVersionId,
            UUID sourceRevisionId,
            UUID sourceAclSnapshotId,
            KnowledgeAssetEvidenceRole evidenceRole,
            Integer spanStart,
            Integer spanEnd) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.knowledgeAssetVersionId =
                Objects.requireNonNull(knowledgeAssetVersionId, "knowledgeAssetVersionId");
        this.sourceRevisionId = Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        this.sourceAclSnapshotId = Objects.requireNonNull(sourceAclSnapshotId, "sourceAclSnapshotId");
        this.evidenceRole = Objects.requireNonNull(evidenceRole, "evidenceRole");
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
    }
}
