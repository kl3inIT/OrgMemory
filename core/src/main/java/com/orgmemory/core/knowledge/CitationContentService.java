package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.List;
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

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");

    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final RelationshipAuthorizationSetPort authorization;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;
    private final SourceRevisionRepository revisions;
    private final EvidenceBlobRepository blobs;
    private final ObjectStoragePort objects;
    private final PermissionAuditService audit;

    CitationContentService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            SourceRevisionRepository revisions,
            EvidenceBlobRepository blobs,
            ObjectStoragePort objects,
            PermissionAuditService audit) {
        this.searchAuthorization = searchAuthorization;
        this.evidenceScopes = evidenceScopes;
        this.authorization = authorization;
        this.canonicalEvidence = canonicalEvidence;
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
        String authorizationModelId = searchAuthorization.require(
                actor,
                normalizedRequestId,
                auditQuery);
        ResolvedKnowledgeEvidenceScope initial =
                resolve(
                        actor,
                        authorizationModelId,
                        normalizedRequestId,
                        auditQuery);
        SecureRetrievalCandidate candidate =
                findCanonical(
                        actor,
                        initial,
                        chunkId,
                        normalizedRequestId,
                        authorizationModelId);
        verifyOpenFga(
                actor,
                candidate,
                normalizedRequestId,
                authorizationModelId);

        ResolvedKnowledgeEvidenceScope current =
                resolve(
                        actor,
                        authorizationModelId,
                        normalizedRequestId,
                        auditQuery);
        SecureRetrievalCandidate currentCandidate =
                findCanonical(
                        actor,
                        current,
                        chunkId,
                        normalizedRequestId,
                        authorizationModelId);
        if (!sameEvidence(candidate, currentCandidate)
                || !sameScopeForAsset(initial, current, candidate)) {
            throw notFound(actor, chunkId, normalizedRequestId,
                    authorizationModelId, "CITATION_AUTHORIZATION_CHANGED");
        }

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
            try {
                content.close();
            } catch (java.io.IOException ignored) {
                // The integrity failure remains the authoritative outcome.
            }
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

    private ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String authorizationModelId,
            String requestId,
            String auditQuery) {
        try {
            return evidenceScopes.resolve(actor, authorizationModelId);
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    auditQuery,
                    unavailable.reasonCode(),
                    unavailable.policyVersion());
        }
    }

    private SecureRetrievalCandidate findCanonical(
            CurrentActor actor,
            ResolvedKnowledgeEvidenceScope scope,
            UUID chunkId,
            String requestId,
            String authorizationModelId) {
        if (scope.allAssetIds().isEmpty()) {
            throw notFound(
                    actor,
                    chunkId,
                    requestId,
                    authorizationModelId,
                    "CITATION_NOT_VISIBLE");
        }
        List<SecureRetrievalCandidate> candidates =
                canonicalEvidence.recheck(
                        retrievalScope(scope),
                        List.of(chunkId));
        if (candidates.size() != 1) {
            throw notFound(
                    actor,
                    chunkId,
                    requestId,
                    authorizationModelId,
                    "CITATION_NOT_VISIBLE");
        }
        return candidates.getFirst();
    }

    private void verifyOpenFga(
            CurrentActor actor,
            SecureRetrievalCandidate candidate,
            String requestId,
            String authorizationModelId) {
        ResourceRef asset = ResourceRef.of(
                actor.organizationId(),
                "knowledge_asset",
                candidate.knowledgeAssetId());
        var checked = authorization.batchCheck(
                new BatchAuthorizationQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        List.of(asset)));
        var decision = checked.decisions().get(asset);
        if (!checked.resolved()
                || !authorizationModelId.equals(checked.policyVersion())
                || decision == null
                || !decision.allowed()
                || !authorizationModelId.equals(
                        decision.policyVersion())) {
            throw notFound(
                    actor,
                    candidate.chunkId(),
                    requestId,
                    authorizationModelId,
                    "CITATION_OPENFGA_RECHECK_DENIED");
        }
    }

    private static SecureKnowledgeRetrievalStore.RetrievalScope
            retrievalScope(ResolvedKnowledgeEvidenceScope scope) {
        return new SecureKnowledgeRetrievalStore.RetrievalScope(
                scope.organizationId(),
                scope.actorUserId(),
                scope.actorDepartmentId(),
                scope.actorExecutive(),
                scope.allAssetIds().stream().sorted().toList(),
                scope.authorizationModelId(),
                scope.evaluatedAt());
    }

    private static boolean sameScopeForAsset(
            ResolvedKnowledgeEvidenceScope initial,
            ResolvedKnowledgeEvidenceScope current,
            SecureRetrievalCandidate candidate) {
        return initial.authorizationModelId()
                        .equals(current.authorizationModelId())
                && initial.allAssetIds().contains(
                        candidate.knowledgeAssetId())
                && current.allAssetIds().contains(
                        candidate.knowledgeAssetId());
    }

    private static boolean sameEvidence(
            SecureRetrievalCandidate initial,
            SecureRetrievalCandidate current) {
        return initial.chunkId().equals(current.chunkId())
                && initial.knowledgeAssetId()
                        .equals(current.knowledgeAssetId())
                && initial.sourceRevisionId()
                        .equals(current.sourceRevisionId())
                && initial.currentAclSnapshotId()
                        .equals(current.currentAclSnapshotId())
                && initial.authorizationModelId()
                        .equals(current.authorizationModelId());
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
