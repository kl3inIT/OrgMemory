package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.sourceledger.EvidenceBlobRepository;
import com.orgmemory.core.knowledge.sourceledger.EvidenceScanStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
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
public class SourceContentService {

    private final KnowledgeEvidenceScopeResolver authorization;
    private final SourceRevisionRepository revisions;
    private final EvidenceBlobRepository blobs;
    private final ObjectStoragePort objects;
    private final PermissionAuditService audit;

    SourceContentService(
            KnowledgeEvidenceScopeResolver authorization,
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
    public SourceContent open(CurrentActor actor, UUID sourceId, String requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(sourceId, "sourceId");
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.strip();
        var scope = authorization.resolve(actor, null);
        var revision = revisions
                .findCurrentReadyBySourceObjectIdAndOrganizationId(
                        sourceId, actor.organizationId())
                .orElseThrow(() -> notFound(
                        actor,
                        sourceId,
                        normalizedRequestId,
                        scope.authorizationModelId(),
                        "SOURCE_NOT_CURRENT"));
        if (revision.getKnowledgeAssetId() == null
                || !scope.allAssetIds().contains(revision.getKnowledgeAssetId())) {
            throw notFound(
                    actor,
                    sourceId,
                    normalizedRequestId,
                    scope.authorizationModelId(),
                    "SOURCE_NOT_AUTHORIZED");
        }
        var blob = blobs
                .findByIdAndOrganizationId(
                        revision.getEvidenceBlobId(), actor.organizationId())
                .filter(value -> value.getScanStatus() == EvidenceScanStatus.BASIC_VALIDATED)
                .orElseThrow(() -> notFound(
                        actor,
                        sourceId,
                        normalizedRequestId,
                        scope.authorizationModelId(),
                        "SOURCE_BLOB_NOT_AVAILABLE"));
        ObjectContent content = objects.open(new ObjectKey(blob.getObjectKey()));
        if (!blob.getContentSha256().equals(revision.getContentSha256())
                || blob.getContentLength() != revision.getContentLength()
                || !blob.getContentSha256().equals(content.metadata().sha256())
                || blob.getContentLength() != content.metadata().contentLength()) {
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
                revision.getId(),
                null,
                revision.getEmbeddingProfileId(),
                null));
        return new SourceContent(
                sourceId,
                revision.getFileName(),
                revision.getMediaType(),
                revision.getContentLength(),
                revision.getContentSha256(),
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
