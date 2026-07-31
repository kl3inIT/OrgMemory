package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.sourceledger.EvidenceBlob;
import com.orgmemory.core.knowledge.sourceledger.EvidenceBlobRepository;
import com.orgmemory.core.knowledge.sourceledger.EvidenceScanStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceRevision;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus;

import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens original source evidence through the same permission boundary used by
 * retrieval. No storage URL or object key crosses the application boundary.
 */
@Service
public class CitationContentService {

    private final CanonicalEvidenceAuthorizationService authorization;
    private final SourceRevisionRepository revisions;
    private final EvidenceBlobRepository blobs;
    private final ObjectStoragePort objects;
    private final PermissionAuditService audit;

    CitationContentService(
            CanonicalEvidenceAuthorizationService authorization,
            SourceRevisionRepository revisions,
            EvidenceBlobRepository blobs,
            ObjectStoragePort objects,
            PermissionAuditService audit) {
        this.authorization = authorization;
        this.revisions = revisions;
        this.blobs = blobs;
        this.objects = objects;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CitationContent open(
            CurrentActor actor,
            UUID chunkId,
            String requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(chunkId, "chunkId");
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.strip();
        String auditQuery = "citation:" + chunkId;
        CanonicalEvidenceAuthorizationService.Verification verified;
        try {
            verified = authorization.verify(
                    actor,
                    normalizedRequestId,
                    auditQuery,
                    java.util.List.of(chunkId));
        } catch (CanonicalEvidenceAuthorizationException denied) {
            throw notFound(
                    actor,
                    chunkId,
                    normalizedRequestId,
                    denied.authorizationModelId(),
                    denied.reasonCode());
        }
        SecureRetrievalCandidate currentCandidate =
                verified.candidates().getFirst();
        String authorizationModelId = verified.authorizationModelId();

        SourceRevision revision = revisions
                .findByIdAndOrganizationId(
                        currentCandidate.sourceRevisionId(),
                        actor.organizationId())
                .filter(value -> value.getStatus()
                        == SourceRevisionStatus.READY)
                .filter(value -> value.getKnowledgeAssetId()
                        .equals(currentCandidate.knowledgeAssetId()))
                .orElseThrow(() -> notFound(
                        actor,
                        chunkId,
                        normalizedRequestId,
                        authorizationModelId,
                        "CITATION_REVISION_NOT_CURRENT"));
        EvidenceBlob blob = blobs
                .findByIdAndOrganizationId(
                        revision.getEvidenceBlobId(),
                        actor.organizationId())
                .filter(value -> value.getScanStatus()
                        == EvidenceScanStatus.BASIC_VALIDATED)
                .orElseThrow(() -> notFound(
                        actor,
                        chunkId,
                        normalizedRequestId,
                        authorizationModelId,
                        "CITATION_BLOB_NOT_AVAILABLE"));

        var content = objects.open(new ObjectKey(blob.getObjectKey()));
        if (!blob.getContentSha256().equals(content.metadata().sha256())
                || blob.getContentLength()
                        != content.metadata().contentLength()) {
            closeQuietly(content);
            throw new KnowledgeRetrievalUnavailableException(
                    "Citation evidence failed its integrity check");
        }
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "READ_CITATION",
                "KNOWLEDGE_CHUNK",
                chunkId.toString(),
                PermissionAuditDecision.ALLOW,
                "AUTHORIZED_CITATION_CONTENT",
                authorizationModelId,
                normalizedRequestId,
                null,
                currentCandidate.ingestionAclSnapshotId(),
                currentCandidate.currentAclSnapshotId(),
                currentCandidate.authorizationModelId(),
                currentCandidate.sourceRevisionId(),
                currentCandidate.chunkId(),
                currentCandidate.embeddingProfileId(),
                currentCandidate.projectionGeneration()));
        return new CitationContent(
                chunkId,
                revision.getFileName(),
                revision.getMediaType(),
                revision.getContentLength(),
                revision.getContentSha256(),
                content);
    }

    private static void closeQuietly(ObjectContent content) {
        try {
            content.close();
        } catch (java.io.IOException ignored) {
            // The authorization or integrity failure is authoritative.
        }
    }

    private CitationNotFoundException notFound(
            CurrentActor actor,
            UUID chunkId,
            String requestId,
            String authorizationModelId,
            String reason) {
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "READ_CITATION",
                "KNOWLEDGE_CHUNK",
                chunkId.toString(),
                PermissionAuditDecision.DENY,
                reason,
                authorizationModelId,
                requestId,
                null));
        return new CitationNotFoundException();
    }
}
