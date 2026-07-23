package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class KnowledgeAssetPublicationCoordinator {

    private final KnowledgeIngestionService ingestion;
    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeAssetPublicationOutboxRepository publications;
    private final KnowledgeChunkProjectionStore chunks;
    private final SourceObjectRepository sources;

    KnowledgeAssetPublicationCoordinator(
            KnowledgeIngestionService ingestion,
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions,
            KnowledgeAssetPublicationOutboxRepository publications,
            KnowledgeChunkProjectionStore chunks,
            SourceObjectRepository sources) {
        this.ingestion = ingestion;
        this.assets = assets;
        this.versions = versions;
        this.publications = publications;
        this.chunks = chunks;
        this.sources = sources;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    KnowledgeAssetPublicationState prepare(PublishKnowledgeAssetCommand command) {
        KnowledgeAssetRef assetRef = ingestion.promote(new PromoteNormalizedRecordCommand(
                command.organizationId(),
                command.knowledgeSpaceId(),
                command.sourceObjectId(),
                command.sourceRevisionId(),
                command.normalizedRecordId(),
                AccessGate.ALLOW));
        var existing = publications.findByKnowledgeAssetVersionId(
                assetRef.knowledgeAssetVersionId());
        if (existing.isPresent()) {
            KnowledgeAssetPublicationOutbox publication = existing.get();
            publication.requireSamePublication(
                    command,
                    assetRef.knowledgeAssetId(),
                    assetRef.knowledgeAssetVersionId());
            return state(publication);
        }
        if (assetRef.status() != KnowledgeAssetVersionStatus.PENDING) {
            throw new KnowledgeIngestionConflictException(
                    "An active knowledge asset version cannot start a new publication");
        }

        assets.flush();
        long projectionGeneration = versions
                .findById(assetRef.knowledgeAssetVersionId())
                .orElseThrow(() -> new IllegalStateException("Knowledge asset version is missing"))
                .getVersionNumber();
        chunks.replace(
                command.organizationId(),
                command.sourceObjectId(),
                command.sourceRevisionId(),
                assetRef.knowledgeAssetId(),
                assetRef.knowledgeAssetVersionId(),
                command.embeddingProfile(),
                command.pipelineVersion(),
                projectionGeneration,
                command.chunks());
        KnowledgeAssetPublicationOutbox publication = publications.saveAndFlush(
                new KnowledgeAssetPublicationOutbox(
                        command,
                        assetRef.knowledgeAssetId(),
                        assetRef.knowledgeAssetVersionId(),
                        projectionGeneration));
        return state(publication);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    KnowledgeAssetPublicationState startAttempt(UUID organizationId, UUID publicationId) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        publication.startAttempt();
        return state(publications.saveAndFlush(publication));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(UUID organizationId, UUID publicationId, String code, String message) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        publication.recordFailure(code, message);
        publications.save(publication);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    KnowledgeAssetRef resolveApplied(UUID organizationId, UUID publicationId) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        if (publication.getStatus() != KnowledgeAssetPublicationStatus.APPLIED) {
            throw new IllegalStateException("Knowledge asset publication has not been applied");
        }
        KnowledgeAsset asset = requiredAsset(publication, organizationId);
        KnowledgeAssetVersion version = requiredVersion(publication, organizationId);
        if (version.getStatus() != KnowledgeAssetVersionStatus.ACTIVE
                || !version.getId().equals(asset.getCurrentVersionId())) {
            throw new IllegalStateException("Applied publication does not have the current active version");
        }
        return assetRef(asset, version);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    KnowledgeAssetRef complete(UUID organizationId, UUID publicationId, String authorizationModelId) {
        KnowledgeAssetPublicationOutbox publication = requiredPublication(organizationId, publicationId);
        KnowledgeAsset asset = requiredAsset(publication, organizationId);
        KnowledgeAssetVersion version = requiredVersion(publication, organizationId);
        if (publication.getStatus() == KnowledgeAssetPublicationStatus.APPLIED) {
            if (version.getStatus() != KnowledgeAssetVersionStatus.ACTIVE
                    || !version.getId().equals(asset.getCurrentVersionId())) {
                throw new IllegalStateException("Applied publication does not have the current active version");
            }
            return assetRef(asset, version);
        }

        int activatedChunks = chunks.activate(
                organizationId,
                publication.getSourceRevisionId(),
                publication.getKnowledgeAssetId(),
                publication.getKnowledgeAssetVersionId(),
                publication.getProjectionGeneration());
        if (activatedChunks <= 0) {
            throw new IllegalStateException("Publication has no inactive chunk projection to activate");
        }
        Instant appliedAt = Instant.now();
        UUID previousVersionId = asset.getCurrentVersionId();
        if (previousVersionId != null && !previousVersionId.equals(version.getId())) {
            KnowledgeAssetVersion previous = versions
                    .findByIdAndOrganizationId(previousVersionId, organizationId)
                    .orElseThrow(() -> new IllegalStateException("Previous knowledge asset version is missing"));
            previous.retire(appliedAt);
            versions.saveAndFlush(previous);
        }
        version.activate(appliedAt);
        asset.useVersion(version.getId());
        SourceObject source = sources.findById(publication.getSourceObjectId())
                .orElseThrow(() -> new IllegalStateException("Publication source object is missing"));
        source.publishRevision(publication.getSourceRevisionId());
        publication.markApplied(authorizationModelId, appliedAt);
        assets.save(asset);
        versions.save(version);
        sources.save(source);
        publications.save(publication);
        return assetRef(asset, version);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    List<KnowledgeAssetPublicationState> findAuthorizationModelDrift(
            String authorizationModelId, int limit) {
        return publications
                .findAuthorizationModelDrift(
                        authorizationModelId,
                        PageRequest.of(0, limit))
                .stream()
                .map(KnowledgeAssetPublicationCoordinator::state)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordAuthorizationModel(
            UUID organizationId,
            UUID publicationId,
            String authorizationModelId) {
        KnowledgeAssetPublicationOutbox publication =
                requiredPublication(organizationId, publicationId);
        publication.recordAuthorizationModel(authorizationModelId);
        publications.save(publication);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Set<UUID> existingAssetIds(Set<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Set.of();
        }
        return assets.findAllById(candidateIds).stream()
                .map(KnowledgeAsset::getId)
                .collect(Collectors.toUnmodifiableSet());
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

    private KnowledgeAssetVersion requiredVersion(
            KnowledgeAssetPublicationOutbox publication, UUID organizationId) {
        return versions
                .findByIdAndOrganizationId(
                        publication.getKnowledgeAssetVersionId(), organizationId)
                .filter(version -> version.getKnowledgeAssetId()
                        .equals(publication.getKnowledgeAssetId()))
                .orElseThrow(() -> new IllegalStateException("Publication knowledge asset version is missing"));
    }

    private static KnowledgeAssetPublicationState state(KnowledgeAssetPublicationOutbox publication) {
        return new KnowledgeAssetPublicationState(
                publication.getId(),
                publication.getOrganizationId(),
                publication.getKnowledgeSpaceId(),
                publication.getSourceRevisionId(),
                publication.getKnowledgeAssetId(),
                publication.getKnowledgeAssetVersionId(),
                publication.getOwnerUserId(),
                publication.getProjectionGeneration(),
                publication.getStatus());
    }

    private static KnowledgeAssetRef assetRef(
            KnowledgeAsset asset, KnowledgeAssetVersion version) {
        return new KnowledgeAssetRef(
                asset.getId(),
                version.getId(),
                version.getNormalizedRecordId(),
                version.getRawSourceObjectId(),
                version.getSourceAclSnapshotId(),
                version.getStatus());
    }
}
