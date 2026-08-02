package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidence;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceResult;

import com.orgmemory.core.knowledge.storage.ObjectContent;
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
class DefaultCitationContentService implements CitationContentService {

    private final CanonicalEvidenceAuthorizationService authorization;
    private final SourceCitationEvidenceQuery evidenceQuery;
    private final ObjectStoragePort objects;
    private final PermissionAuditService audit;

    DefaultCitationContentService(
            CanonicalEvidenceAuthorizationService authorization,
            SourceCitationEvidenceQuery evidenceQuery,
            ObjectStoragePort objects,
            PermissionAuditService audit) {
        this.authorization = authorization;
        this.evidenceQuery = evidenceQuery;
        this.objects = objects;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    @Override
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

        SourceCitationEvidenceResult result = evidenceQuery.findAvailable(
                actor.organizationId(),
                currentCandidate.sourceRevisionId(),
                currentCandidate.knowledgeAssetId());
        SourceCitationEvidence evidence = switch (result) {
            case SourceCitationEvidenceResult.Available available ->
                    available.evidence();
            case SourceCitationEvidenceResult.Unavailable unavailable -> {
                String reason = switch (unavailable.reason()) {
                    case REVISION_NOT_CURRENT -> "CITATION_REVISION_NOT_CURRENT";
                    case BLOB_NOT_AVAILABLE -> "CITATION_BLOB_NOT_AVAILABLE";
                };
                throw notFound(
                        actor,
                        chunkId,
                        normalizedRequestId,
                        authorizationModelId,
                        reason);
            }
        };

        var content = objects.open(evidence.objectKey());
        if (!evidence.storedContentSha256().equals(content.metadata().sha256())
                || evidence.storedContentLength()
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
                evidence.fileName(),
                evidence.mediaType(),
                evidence.contentLength(),
                evidence.contentSha256(),
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
