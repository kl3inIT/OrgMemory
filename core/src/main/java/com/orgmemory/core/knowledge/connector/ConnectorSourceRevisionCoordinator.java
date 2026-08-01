package com.orgmemory.core.knowledge.connector;

import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRef;


import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.core.knowledge.sourceledger.CompleteSourceRevisionCommand;
import com.orgmemory.core.knowledge.sourceledger.NormalizedRecordRef;
import com.orgmemory.core.knowledge.sourceledger.RawSourceRef;
import com.orgmemory.core.knowledge.sourceledger.SourceEmbeddingProfileRef;
import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionDraftRef;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionService;
import com.orgmemory.core.knowledge.sourceledger.StageSourceRevisionCommand;

import com.orgmemory.core.knowledge.storage.StoredObject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Translates connector crawl facts into Source Ledger-owned revision commands.
 *
 * <p>Source Ledger owns the {@code REQUIRES_NEW} transaction phases because publication runs in
 * its own transaction and reads what staging wrote. This adapter owns only the translation from
 * connector profiles and Asset/embedding results into Source Ledger contracts.
 *
 * <p>Which source an object belongs to comes from the batch's profile rather than from a name
 * compiled in here: this coordinator is the same for every adapter, and the ledger has no reason
 * to know which one is calling.
 */
@Service
class ConnectorSourceRevisionCoordinator {

    private final SourceRevisionService revisions;

    ConnectorSourceRevisionCoordinator(SourceRevisionService revisions) {
        this.revisions = revisions;
    }

    Optional<SourceRevisionDraftRef> findExisting(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            String contentSha256) {
        return revisions.findExisting(
                ctx.organizationId(),
                ctx.profile().sourceSystem(),
                ctx.sourceConnectionKey(),
                content.externalObjectId(),
                contentSha256);
    }

    SourceRevisionDraftRef stage(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            UUID proposedSourceId,
            UUID revisionId,
            UUID blobId,
            StoredObject stored) {
        return revisions.stage(new StageSourceRevisionCommand(
                ctx.organizationId(),
                ctx.knowledgeSpaceId(),
                ctx.actorUserId(),
                ctx.profile().aclAuthority(),
                ctx.profile().sourceSystem(),
                ctx.sourceConnectionKey(),
                content.externalObjectId(),
                content.title(),
                ctx.profile().classification(),
                ctx.profile().declaredAccess(),
                proposedSourceId,
                revisionId,
                blobId,
                stored));
    }

    void complete(
            SourceRevisionDraftRef draft,
            String pipelineVersion,
            String parserVersion,
            String chunkerVersion,
            DocumentProcessingProfileSnapshot processingProfile,
            EmbeddingProfileRef embeddingProfile,
            RawSourceRef raw,
            NormalizedRecordRef normalized,
            KnowledgeAssetRef asset) {
        revisions.complete(new CompleteSourceRevisionCommand(
                draft,
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
                        asset.sourceAclSnapshotId())));
    }
}
