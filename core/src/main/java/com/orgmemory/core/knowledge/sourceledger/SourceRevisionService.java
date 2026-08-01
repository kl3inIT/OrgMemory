package com.orgmemory.core.knowledge.sourceledger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Source Ledger-owned transaction boundary for external-source revisions. */
@Service
public class SourceRevisionService {

    private final SourceObjectRepository sources;
    private final EvidenceBlobRepository blobs;
    private final SourceRevisionRepository revisions;
    private final SourceGraphIndexPort graphIndexJobs;

    SourceRevisionService(
            SourceObjectRepository sources,
            EvidenceBlobRepository blobs,
            SourceRevisionRepository revisions,
            SourceGraphIndexPort graphIndexJobs) {
        this.sources = sources;
        this.blobs = blobs;
        this.revisions = revisions;
        this.graphIndexJobs = graphIndexJobs;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<SourceRevisionDraftRef> findExisting(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId,
            String contentSha256) {
        return sources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        organizationId,
                        sourceSystem,
                        sourceConnectionKey,
                        externalObjectId)
                .flatMap(source -> revisions
                        .findBySourceObjectIdAndContentSha256(
                                source.getId(), contentSha256)
                        .map(revision -> draft(source, revision)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SourceRevisionDraftRef stage(StageSourceRevisionCommand command) {
        SourceObject source = sources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        command.organizationId(),
                        command.sourceSystem(),
                        command.sourceConnectionKey(),
                        command.externalObjectId())
                .orElseGet(() -> sources.saveAndFlush(SourceObject.connectorObject(
                        command.proposedSourceObjectId(),
                        command.organizationId(),
                        command.knowledgeSpaceId(),
                        null,
                        command.createdByUserId(),
                        command.aclAuthority(),
                        command.sourceSystem(),
                        command.sourceConnectionKey(),
                        command.externalObjectId(),
                        command.title(),
                        command.classification(),
                        command.declaredAccess())));
        if (source.getStatus() != SourceObjectStatus.ACTIVE) {
            throw new KnowledgeIngestionConflictException(
                    "a retired object cannot take a new content revision: "
                            + command.externalObjectId());
        }
        var existing = revisions.findBySourceObjectIdAndContentSha256(
                source.getId(), command.storedObject().sha256());
        if (existing.isPresent()) {
            return draft(source, existing.get());
        }

        long revisionNumber = revisions.maximumRevisionNumber(source.getId()) + 1;
        EvidenceBlob blob = blobs.saveAndFlush(new EvidenceBlob(
                command.evidenceBlobId(),
                command.organizationId(),
                command.storedObject()));
        SourceRevision revision = revisions.saveAndFlush(new SourceRevision(
                command.sourceRevisionId(),
                source,
                blob,
                command.title(),
                revisionNumber));
        source.stageRevision(revision.getId());
        sources.saveAndFlush(source);
        return draft(source, revision);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(CompleteSourceRevisionCommand command) {
        SourceRevisionDraftRef draft = command.draft();
        SourceRevision revision = revisions.findById(draft.sourceRevisionId()).orElseThrow();
        Instant completedAt = Instant.now();
        revision.ready(
                command.pipelineVersion(),
                command.parserVersion(),
                command.chunkerVersion(),
                command.processingProfile(),
                command.embeddingProfile(),
                command.rawSource(),
                command.normalizedRecord(),
                command.knowledgeAsset(),
                completedAt);
        SourceObject source = sources.findById(draft.sourceObjectId()).orElseThrow();
        source.publishRevision(revision.getId());
        revisions.saveAndFlush(revision);
        graphIndexJobs.enqueue(
                revision.getOrganizationId(),
                revision.getId(),
                command.knowledgeAsset().knowledgeAssetId(),
                command.knowledgeAsset().knowledgeAssetVersionId(),
                completedAt);
        sources.save(source);
    }

    private static SourceRevisionDraftRef draft(
            SourceObject source, SourceRevision revision) {
        return new SourceRevisionDraftRef(
                source.getId(),
                revision.getId(),
                revision.getRevisionNumber(),
                revision.getStatus() == SourceRevisionStatus.READY);
    }
}
