package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.storage.StoredObject;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Owns the PostgreSQL phases around connector evidence and source revisions. */
@Service
class ConnectorSourceRevisionCoordinator {

    private final SourceObjectRepository sources;
    private final EvidenceBlobRepository blobs;
    private final SourceRevisionRepository revisions;
    private final GraphIndexJobQueue graphIndexJobs;

    ConnectorSourceRevisionCoordinator(
            SourceObjectRepository sources,
            EvidenceBlobRepository blobs,
            SourceRevisionRepository revisions,
            GraphIndexJobQueue graphIndexJobs) {
        this.sources = sources;
        this.blobs = blobs;
        this.revisions = revisions;
        this.graphIndexJobs = graphIndexJobs;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Optional<ConnectorRevisionDraft> findExisting(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            String contentSha256) {
        return sources
                .findByOrganizationIdAndSourceTypeAndSourceConnectionKeyAndExternalObjectId(
                        ctx.organizationId(),
                        SlackConnectorProfile.SOURCE_TYPE,
                        ctx.sourceConnectionKey(),
                        content.externalObjectId())
                .flatMap(source -> revisions
                        .findBySourceObjectIdAndContentSha256(source.getId(), contentSha256)
                        .map(revision -> draft(source, revision, true)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ConnectorRevisionDraft stage(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            UUID proposedSourceId,
            UUID revisionId,
            UUID blobId,
            StoredObject stored) {
        SourceObject source = sources
                .findByOrganizationIdAndSourceTypeAndSourceConnectionKeyAndExternalObjectId(
                        ctx.organizationId(),
                        SlackConnectorProfile.SOURCE_TYPE,
                        ctx.sourceConnectionKey(),
                        content.externalObjectId())
                .orElseGet(() -> sources.saveAndFlush(SourceObject.connectorObject(
                        proposedSourceId,
                        ctx.organizationId(),
                        ctx.knowledgeSpaceId(),
                        null,
                        ctx.actorUserId(),
                        SlackConnectorProfile.SOURCE_TYPE,
                        ctx.sourceConnectionKey(),
                        content.externalObjectId(),
                        content.title(),
                        SlackConnectorProfile.CLASSIFICATION,
                        SlackConnectorProfile.DECLARED_ACCESS)));
        if (source.getStatus() != SourceObjectStatus.ACTIVE) {
            throw new KnowledgeIngestionConflictException(
                    "An archived connector source cannot be resurrected implicitly");
        }
        var existing = revisions.findBySourceObjectIdAndContentSha256(
                source.getId(), stored.sha256());
        if (existing.isPresent()) {
            return draft(source, existing.get(), true);
        }

        long revisionNumber = revisions.maximumRevisionNumber(source.getId()) + 1;
        EvidenceBlob blob = blobs.saveAndFlush(new EvidenceBlob(blobId, ctx.organizationId(), stored));
        SourceRevision revision = revisions.saveAndFlush(new SourceRevision(
                revisionId, source, blob, content.title(), revisionNumber));
        source.stageRevision(revision.getId());
        sources.saveAndFlush(source);
        return draft(source, revision, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(
            ConnectorRevisionDraft draft,
            String pipelineVersion,
            String parserVersion,
            String chunkerVersion,
            EmbeddingProfileRef embeddingProfile,
            RawSourceRef raw,
            NormalizedRecordRef normalized,
            KnowledgeAssetRef asset) {
        SourceRevision revision = revisions.findById(draft.sourceRevisionId()).orElseThrow();
        Instant completedAt = Instant.now();
        revision.ready(
                pipelineVersion,
                parserVersion,
                chunkerVersion,
                embeddingProfile,
                raw,
                normalized,
                asset,
                completedAt);
        SourceObject source = sources.findById(draft.sourceObjectId()).orElseThrow();
        source.publishRevision(revision.getId());
        revisions.saveAndFlush(revision);
        graphIndexJobs.enqueue(
                revision.getOrganizationId(), revision.getId(), asset, completedAt);
        sources.save(source);
    }

    private static ConnectorRevisionDraft draft(
            SourceObject source, SourceRevision revision, boolean existing) {
        return new ConnectorRevisionDraft(
                source.getId(), revision.getId(), revision.getRevisionNumber(), existing);
    }
}
