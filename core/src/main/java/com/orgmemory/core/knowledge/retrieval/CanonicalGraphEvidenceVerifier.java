package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CanonicalGraphEvidenceVerifier implements GraphEvidenceVerifier {

    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;

    CanonicalGraphEvidenceVerifier(
            KnowledgeEvidenceScopeResolver evidenceScopes,
            SecureKnowledgeRetrievalStore canonicalEvidence) {
        this.evidenceScopes = evidenceScopes;
        this.canonicalEvidence = canonicalEvidence;
    }

    @Override
    public VerifiedGraphEvidenceScope verifyScope(
            CurrentActor actor,
            String expectedAuthorizationModelId) {
        try {
            ResolvedKnowledgeEvidenceScope resolved = evidenceScopes.resolve(
                    Objects.requireNonNull(actor, "actor"),
                    expectedAuthorizationModelId);
            return new VerifiedGraphEvidenceScope(
                    resolved.organizationId(),
                    resolved.actorUserId(),
                    resolved.actorDepartmentId(),
                    resolved.actorExecutive(),
                    resolved.authorizationModelId(),
                    resolved.evaluatedAt(),
                    resolved.assetIdsByKnowledgeSpace(),
                    resolved.aclGenerationByKnowledgeSpace());
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Canonical Graph evidence scope is unavailable",
                    unavailable);
        }
    }

    @Override
    public boolean isCurrentGoverningEvidence(
            VerifiedGraphEvidenceScope scope,
            UUID knowledgeSpaceId,
            EvidenceReference evidence) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(evidence, "evidence");
        if (!scope.includes(
                knowledgeSpaceId,
                evidence.organizationId(),
                evidence.knowledgeAssetId())) {
            return false;
        }
        var candidates = canonicalEvidence.recheck(
                scope.toRetrievalScope(),
                List.of(Objects.requireNonNull(
                        evidence.chunkId(), "governing evidence chunkId")));
        return candidates.size() == 1
                && candidates.getFirst().organizationId()
                        .equals(evidence.organizationId())
                && candidates.getFirst().knowledgeAssetId()
                        .equals(evidence.knowledgeAssetId())
                && candidates.getFirst().sourceRevisionId()
                        .equals(evidence.sourceRevisionId())
                && candidates.getFirst().currentAclSnapshotId()
                        .equals(evidence.aclSnapshotId())
                && candidates.getFirst().chunkId()
                        .equals(evidence.chunkId());
    }
}
