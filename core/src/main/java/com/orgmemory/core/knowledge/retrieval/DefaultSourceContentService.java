package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.sourceledger.SourceDocumentEvidenceQuery;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens a current document through the canonical evidence authorization scope. */
@Service
class DefaultSourceContentService implements SourceContentService {

    private final KnowledgeEvidenceScopeResolver authorization;
    private final SourceDocumentEvidenceQuery evidenceQuery;
    private final ObjectStoragePort objects;
    private final PermissionAuditService audit;

    DefaultSourceContentService(
            KnowledgeEvidenceScopeResolver authorization,
            SourceDocumentEvidenceQuery evidenceQuery,
            ObjectStoragePort objects,
            PermissionAuditService audit) {
        this.authorization = authorization;
        this.evidenceQuery = evidenceQuery;
        this.objects = objects;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    @Override
    public SourceContent open(CurrentActor actor, UUID sourceId, String requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(sourceId, "sourceId");
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.strip();
        var scope = authorization.resolve(actor, null);
        var document = evidenceQuery
                .findAvailable(actor.organizationId(), sourceId)
                .orElseThrow(() -> notFound(
                        actor,
                        sourceId,
                        normalizedRequestId,
                        scope.authorizationModelId(),
                        "SOURCE_NOT_CURRENT"));
        if (!scope.allAssetIds().contains(document.knowledgeAssetId())) {
            throw notFound(
                    actor,
                    sourceId,
                    normalizedRequestId,
                    scope.authorizationModelId(),
                    "SOURCE_NOT_AUTHORIZED");
        }
        var evidence = document.evidence();
        ObjectContent content = objects.open(evidence.objectKey());
        if (!evidence.storedContentSha256().equals(evidence.contentSha256())
                || evidence.storedContentLength() != evidence.contentLength()
                || !evidence.storedContentSha256().equals(content.metadata().sha256())
                || evidence.storedContentLength() != content.metadata().contentLength()) {
            closeQuietly(content);
            audit.record(new PermissionAuditCommand(
                    actor.organizationId(),
                    actor.userId(),
                    "READ_SOURCE_CONTENT",
                    "SOURCE_OBJECT",
                    sourceId.toString(),
                    PermissionAuditDecision.DENY,
                    "SOURCE_BLOB_INTEGRITY_FAILED",
                    scope.authorizationModelId(),
                    normalizedRequestId,
                    null));
            throw new KnowledgeRetrievalUnavailableException(
                    "Source evidence failed its integrity check");
        }
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "READ_SOURCE_CONTENT",
                "SOURCE_OBJECT",
                sourceId.toString(),
                PermissionAuditDecision.ALLOW,
                "AUTHORIZED_SOURCE_CONTENT",
                scope.authorizationModelId(),
                normalizedRequestId,
                null,
                null,
                null,
                scope.authorizationModelId(),
                document.sourceRevisionId(),
                null,
                document.embeddingProfileId(),
                null));
        return new SourceContent(
                sourceId,
                evidence.fileName(),
                evidence.mediaType(),
                evidence.contentLength(),
                evidence.contentSha256(),
                content);
    }

    private KnowledgeResourceNotFoundException notFound(
            CurrentActor actor,
            UUID sourceId,
            String requestId,
            String policyVersion,
            String reason) {
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "READ_SOURCE_CONTENT",
                "SOURCE_OBJECT",
                sourceId.toString(),
                PermissionAuditDecision.DENY,
                reason,
                policyVersion,
                requestId,
                null));
        return new KnowledgeResourceNotFoundException();
    }

    private static void closeQuietly(ObjectContent content) {
        try {
            content.close();
        } catch (java.io.IOException ignored) {
            // Integrity failure is authoritative.
        }
    }
}
