package com.orgmemory.core.knowledge.asset;

import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only Asset-owned boundary for graph indexing and curation. */
@Service
public class KnowledgeAssetGraphQuery {

    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeChunkProjectionStore chunks;

    KnowledgeAssetGraphQuery(
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions,
            KnowledgeChunkProjectionStore chunks) {
        this.assets = assets;
        this.versions = versions;
        this.chunks = chunks;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeAssetGraphRef> findAsset(
            UUID organizationId, UUID knowledgeAssetId) {
        return assets.findByIdAndOrganizationId(
                        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId"),
                        Objects.requireNonNull(organizationId, "organizationId"))
                .map(KnowledgeAssetGraphQuery::assetRef);
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeAssetVersionGraphRef> findVersion(
            UUID organizationId, UUID knowledgeAssetVersionId) {
        return versions.findByIdAndOrganizationId(
                        Objects.requireNonNull(
                                knowledgeAssetVersionId, "knowledgeAssetVersionId"),
                        Objects.requireNonNull(organizationId, "organizationId"))
                .map(KnowledgeAssetGraphQuery::versionRef);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeAssetGraphChunk> loadActiveChunks(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            long projectionGeneration) {
        return chunks.loadActive(
                        Objects.requireNonNull(organizationId, "organizationId"),
                        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId"),
                        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId"),
                        Objects.requireNonNull(
                                knowledgeAssetVersionId, "knowledgeAssetVersionId"),
                        projectionGeneration)
                .stream()
                .map(chunk -> new KnowledgeAssetGraphChunk(
                        chunk.id(),
                        chunk.index(),
                        chunk.content(),
                        chunk.heading(),
                        chunk.tokenCount(),
                        chunk.embedding()))
                .toList();
    }

    @Transactional(readOnly = true)
    public void requireInSpace(
            UUID organizationId, UUID knowledgeAssetId, UUID knowledgeSpaceId) {
        KnowledgeAssetGraphRef asset = findAsset(organizationId, knowledgeAssetId)
                .orElseThrow(KnowledgeAssetNotFoundException::new);
        if (!Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId")
                .equals(asset.knowledgeSpaceId())) {
            throw new KnowledgeResourceNotFoundException();
        }
    }

    private static KnowledgeAssetGraphRef assetRef(KnowledgeAsset asset) {
        return new KnowledgeAssetGraphRef(
                asset.getId(),
                asset.getKnowledgeSpaceId(),
                asset.getCurrentVersionId(),
                asset.getArchivedAt() != null);
    }

    private static KnowledgeAssetVersionGraphRef versionRef(
            KnowledgeAssetVersion version) {
        return new KnowledgeAssetVersionGraphRef(
                version.getId(),
                version.getKnowledgeAssetId(),
                version.getSourceRevisionId(),
                version.getSourceAclSnapshotId(),
                version.getVersionNumber(),
                version.getLanguage(),
                version.getStatus() == KnowledgeAssetVersionStatus.ACTIVE);
    }
}
