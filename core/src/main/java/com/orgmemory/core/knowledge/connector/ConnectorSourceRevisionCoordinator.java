package com.orgmemory.core.knowledge.connector;

import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.graph.GraphIndexJobQueue;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRef;


import com.orgmemory.core.knowledge.acl.AclAuthority;

import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.core.knowledge.sourceledger.EvidenceBlob;
import com.orgmemory.core.knowledge.sourceledger.EvidenceBlobRepository;
import com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionConflictException;
import com.orgmemory.core.knowledge.sourceledger.NormalizedRecordRef;
import com.orgmemory.core.knowledge.sourceledger.RawSourceRef;
import com.orgmemory.core.knowledge.sourceledger.SourceObject;
import com.orgmemory.core.knowledge.sourceledger.SourceObjectRepository;
import com.orgmemory.core.knowledge.sourceledger.SourceObjectStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceEmbeddingProfileRef;
import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef;
import com.orgmemory.core.knowledge.sourceledger.SourceRevision;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository;

import com.orgmemory.core.knowledge.storage.StoredObject;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the PostgreSQL phases around connector evidence and source revisions.
 *
 * <p>Every method commits on its own ({@code REQUIRES_NEW}) because publication runs in its own
 * transaction too and reads what these wrote. A caller that held them all inside one transaction
 * would publish against rows nothing outside it can see yet.
 *
 * <p>Which source an object belongs to comes from the batch's profile rather than from a name
 * compiled in here: this coordinator is the same for every adapter, and the ledger has no reason
 * to know which one is calling.
 */
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
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ctx.organizationId(),
                        ctx.profile().sourceSystem(),
                        ctx.sourceConnectionKey(),
                        content.externalObjectId())
                .flatMap(source -> revisions
                        .findBySourceObjectIdAndContentSha256(source.getId(), contentSha256)
                        .map(revision -> draft(source, revision)));
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
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ctx.organizationId(),
                        ctx.profile().sourceSystem(),
                        ctx.sourceConnectionKey(),
                        content.externalObjectId())
                .orElseGet(() -> sources.saveAndFlush(SourceObject.connectorObject(
                        proposedSourceId,
                        ctx.organizationId(),
                        ctx.knowledgeSpaceId(),
                        null,
                        ctx.actorUserId(),
                        ctx.profile().aclAuthority(),
                        ctx.profile().sourceSystem(),
                        ctx.sourceConnectionKey(),
                        content.externalObjectId(),
                        content.title(),
                        ctx.profile().classification(),
                        ctx.profile().declaredAccess())));
        // A retired object is not revived here. Whether content reappearing at the source should
        // resurrect a tombstoned object, and under whose ACL, is a decision this path must not
        // make silently, so it fails as an isolated per-object error instead.
        if (source.getStatus() != SourceObjectStatus.ACTIVE) {
            throw new KnowledgeIngestionConflictException(
                    "a retired object cannot take a new content revision: "
                            + content.externalObjectId());
        }
        var existing = revisions.findBySourceObjectIdAndContentSha256(
                source.getId(), stored.sha256());
        if (existing.isPresent()) {
            return draft(source, existing.get());
        }

        long revisionNumber = revisions.maximumRevisionNumber(source.getId()) + 1;
        EvidenceBlob blob = blobs.saveAndFlush(new EvidenceBlob(blobId, ctx.organizationId(), stored));
        SourceRevision revision = revisions.saveAndFlush(new SourceRevision(
                revisionId, source, blob, content.title(), revisionNumber));
        // Staged, not current: the object points at this revision as its latest, but retrieval
        // keeps serving the previous one until the chunks exist. Publication advances it.
        source.stageRevision(revision.getId());
        sources.saveAndFlush(source);
        return draft(source, revision);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(
            ConnectorRevisionDraft draft,
            String pipelineVersion,
            String parserVersion,
            String chunkerVersion,
            DocumentProcessingProfileSnapshot processingProfile,
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
                processingProfile,
                new SourceEmbeddingProfileRef(
                        embeddingProfile.id(), embeddingProfile.dimensions()),
                raw,
                normalized,
                new SourceKnowledgeAssetRef(
                        asset.knowledgeAssetId(),
                        asset.knowledgeAssetVersionId(),
                        asset.normalizedRecordId(),
                        asset.rawSourceObjectId(),
                        asset.sourceAclSnapshotId()),
                completedAt);
        SourceObject source = sources.findById(draft.sourceObjectId()).orElseThrow();
        source.publishRevision(revision.getId());
        revisions.saveAndFlush(revision);
        graphIndexJobs.enqueue(
                revision.getOrganizationId(),
                revision.getId(),
                asset.knowledgeAssetId(),
                asset.knowledgeAssetVersionId(),
                completedAt);
        sources.save(source);
    }

    private static ConnectorRevisionDraft draft(
            SourceObject source, SourceRevision revision) {
        return new ConnectorRevisionDraft(
                source.getId(),
                revision.getId(),
                revision.getRevisionNumber(),
                revision.getStatus() == com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus.READY);
    }
}
