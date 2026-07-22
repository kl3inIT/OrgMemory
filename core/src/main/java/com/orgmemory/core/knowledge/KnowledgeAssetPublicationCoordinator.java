package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class KnowledgeAssetPublicationCoordinator {

    private final KnowledgeIngestionService ingestion;
    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetPublicationOutboxRepository publications;
    private final KnowledgeChunkProjectionStore chunks;

    KnowledgeAssetPublicationCoordinator(
            KnowledgeIngestionService ingestion,
            KnowledgeAssetRepository assets,
            KnowledgeAssetPublicationOutboxRepository publications,
            KnowledgeChunkProjectionStore chunks) {
        this.ingestion = ingestion;
        this.assets = assets;
        this.publications = publications;
        this.chunks = chunks;
    }

    @Transactional
    KnowledgeAssetPublicationState prepare(PublishKnowledgeAssetCommand command) {
        KnowledgeAssetRef asset = ingestion.promote(new PromoteNormalizedRecordCommand(
                command.organizationId(), command.normalizedRecordId(), AccessGate.ALLOW));
        var existing = publications.findByKnowledgeAssetId(asset.knowledgeAssetId());
        if (existing.isPresent()) {
            KnowledgeAssetPublicationOutbox publication = existing.get();
            publication.requireSamePublication(command, asset.knowledgeAssetId());
            return state(publication);
        }
        if (asset.status() != KnowledgeAssetStatus.PENDING) {
            throw new KnowledgeIngestionConflictException(
                    "An active knowledge asset cannot start a new initial publication");
        }

        assets.flush();
        chunks.replace(
                command.organizationId(),
                command.sourceObjectId(),
                command.sourceRevisionId(),
                asset.knowledgeAssetId(),
                command.embeddingProfile(),
                command.pipelineVersion(),
                command.projectionGeneration(),
                command.chunks());
        KnowledgeAssetPublicationOutbox publication = publications.saveAndFlush(
                new KnowledgeAssetPublicationOutbox(command, asset.knowledgeAssetId()));
        return state(publication);
    }

    @Transactional
    KnowledgeAssetPublicationState startAttempt(UUID organizationId, UUID publicationId) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        publication.startAttempt();
        return state(publications.saveAndFlush(publication));
    }

    @Transactional
    void recordFailure(UUID organizationId, UUID publicationId, String code, String message) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        publication.recordFailure(code, message);
        publications.save(publication);
    }

    @Transactional(readOnly = true)
    KnowledgeAssetRef resolveApplied(UUID organizationId, UUID publicationId) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        if (publication.getStatus() != KnowledgeAssetPublicationStatus.APPLIED) {
            throw new IllegalStateException("Knowledge asset publication has not been applied");
        }
        KnowledgeAsset asset = requiredAsset(publication, organizationId);
        if (asset.getStatus() != KnowledgeAssetStatus.ACTIVE) {
            throw new IllegalStateException("Applied publication does not have an active asset");
        }
        return assetRef(asset);
    }

    @Transactional
    KnowledgeAssetRef complete(UUID organizationId, UUID publicationId, String authorizationModelId) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        KnowledgeAsset asset = requiredAsset(publication, organizationId);
        if (publication.getStatus() == KnowledgeAssetPublicationStatus.APPLIED) {
            if (asset.getStatus() != KnowledgeAssetStatus.ACTIVE) {
                throw new IllegalStateException("Applied publication does not have an active asset");
            }
            return assetRef(asset);
        }

        int activatedChunks = chunks.activate(
                organizationId,
                publication.getSourceRevisionId(),
                publication.getKnowledgeAssetId(),
                publication.getProjectionGeneration());
        if (activatedChunks <= 0) {
            throw new IllegalStateException("Publication has no inactive chunk projection to activate");
        }
        Instant appliedAt = Instant.now();
        asset.activate(appliedAt);
        publication.markApplied(authorizationModelId, appliedAt);
        assets.save(asset);
        publications.save(publication);
        return assetRef(asset);
    }

    private KnowledgeAssetPublicationOutbox requiredPublication(UUID organizationId, UUID publicationId) {
        return publications.findByIdAndOrganizationId(publicationId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge asset publication was not found"));
    }

    private KnowledgeAsset requiredAsset(
            KnowledgeAssetPublicationOutbox publication, UUID organizationId) {
        return assets.findByIdAndOrganizationId(publication.getKnowledgeAssetId(), organizationId)
                .orElseThrow(() -> new IllegalStateException("Publication knowledge asset is missing"));
    }

    private static KnowledgeAssetPublicationState state(KnowledgeAssetPublicationOutbox publication) {
        return new KnowledgeAssetPublicationState(
                publication.getId(),
                publication.getOrganizationId(),
                publication.getSourceRevisionId(),
                publication.getKnowledgeAssetId(),
                publication.getOwnerUserId(),
                publication.getProjectionGeneration(),
                publication.getStatus());
    }

    private static KnowledgeAssetRef assetRef(KnowledgeAsset asset) {
        return new KnowledgeAssetRef(
                asset.getId(),
                asset.getNormalizedRecordId(),
                asset.getRawSourceObjectId(),
                asset.getSourceAclSnapshotId(),
                asset.getStatus());
    }
}
