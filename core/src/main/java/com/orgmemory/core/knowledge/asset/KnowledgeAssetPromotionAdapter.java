package com.orgmemory.core.knowledge.asset;

import com.orgmemory.core.knowledge.sourceledger.KnowledgeAssetPromotionPort;
import com.orgmemory.core.knowledge.sourceledger.KnowledgeAssetPromotionRequest;
import com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionConflictException;
import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Asset-owned persistence adapter for source-ledger promotion requests. */
@Service
class KnowledgeAssetPromotionAdapter implements KnowledgeAssetPromotionPort {

    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeAssetEvidenceLinkRepository evidence;

    KnowledgeAssetPromotionAdapter(
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions,
            KnowledgeAssetEvidenceLinkRepository evidence) {
        this.assets = assets;
        this.versions = versions;
        this.evidence = evidence;
    }

    @Override
    public Optional<SourceKnowledgeAssetRef> findByNormalizedRecord(
            UUID organizationId, UUID knowledgeSpaceId, UUID normalizedRecordId) {
        return versions.findByNormalizedRecordId(normalizedRecordId)
                .filter(version -> organizationId.equals(version.getOrganizationId()))
                .map(version -> {
                    KnowledgeAsset asset = requiredAsset(version, organizationId);
                    if (!asset.getKnowledgeSpaceId().equals(knowledgeSpaceId)) {
                        throw new KnowledgeIngestionConflictException(
                                "The normalized record is already assigned to another Knowledge Space");
                    }
                    return ref(asset, version);
                });
    }

    @Override
    public SourceKnowledgeAssetRef promote(KnowledgeAssetPromotionRequest request) {
        KnowledgeAsset asset = assets
                .findByOrganizationIdAndSourceObjectId(
                        request.organizationId(), request.sourceObjectId())
                .orElseGet(() -> assets.save(new KnowledgeAsset(
                        request.organizationId(),
                        request.knowledgeSpaceId(),
                        request.sourceObjectId())));
        if (!asset.getKnowledgeSpaceId().equals(request.knowledgeSpaceId())) {
            throw new KnowledgeIngestionConflictException(
                    "The source is already assigned to another Knowledge Space");
        }
        long versionNumber = versions.maximumVersionNumber(asset.getId()) + 1;
        KnowledgeAssetVersion version = versions.save(new KnowledgeAssetVersion(
                asset,
                versionNumber,
                request.sourceRevisionId(),
                draft(request)));
        evidence.save(KnowledgeAssetEvidenceLink.primary(
                request.organizationId(),
                version.getId(),
                request.sourceRevisionId(),
                request.sourceAclSnapshotId()));
        return ref(asset, version);
    }

    private static KnowledgeAssetVersionDraft draft(
            KnowledgeAssetPromotionRequest request) {
        return new KnowledgeAssetVersionDraft(
                request.organizationId(),
                request.rawSourceObjectId(),
                request.normalizedRecordId(),
                request.sourceAclSnapshotId(),
                request.departmentId(),
                request.title(),
                request.normalizedContent(),
                request.language(),
                request.classification(),
                request.declaredAccess(),
                request.contentSha256(),
                request.orgMemoryGate());
    }

    private KnowledgeAsset requiredAsset(
            KnowledgeAssetVersion version, UUID organizationId) {
        return assets.findByIdAndOrganizationId(
                        version.getKnowledgeAssetId(), organizationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Knowledge asset identity is missing"));
    }

    private static SourceKnowledgeAssetRef ref(
            KnowledgeAsset asset, KnowledgeAssetVersion version) {
        return new SourceKnowledgeAssetRef(
                asset.getId(),
                version.getId(),
                version.getNormalizedRecordId(),
                version.getRawSourceObjectId(),
                version.getSourceAclSnapshotId());
    }
}
