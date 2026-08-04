package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.sourceledger.KnowledgeContentType;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidence;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultCitationEvidenceService implements CitationEvidenceService {

    private static final int MAX_EXCERPT_CODE_POINTS = 4_000;

    private final CanonicalEvidenceAuthorizationService authorization;
    private final SourceCitationEvidenceQuery evidenceQuery;
    private final PermissionAuditService audit;

    DefaultCitationEvidenceService(
            CanonicalEvidenceAuthorizationService authorization,
            SourceCitationEvidenceQuery evidenceQuery,
            PermissionAuditService audit) {
        this.authorization = authorization;
        this.evidenceQuery = evidenceQuery;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CitationEvidenceReference> hydrate(
            CurrentActor actor, List<UUID> chunkIds, String requestId) {
        Objects.requireNonNull(actor, "actor");
        List<UUID> unique = List.copyOf(Objects.requireNonNull(chunkIds, "chunkIds"))
                .stream()
                .distinct()
                .toList();
        if (unique.size() > 100) {
            throw new IllegalArgumentException("At most 100 citations may be hydrated");
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        return authorization.filterVisible(
                        actor,
                        requestId,
                        "citation-hydration",
                        unique)
                .candidates()
                .stream()
                .map(candidate -> new CitationEvidenceReference(
                        candidate.chunkId(),
                        candidate.title(),
                        candidate.heading(),
                        candidate.startPage(),
                        candidate.endPage()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CitationEvidenceExcerpt excerpt(
            CurrentActor actor, UUID chunkId, String requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(chunkId, "chunkId");
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.strip();
        CanonicalEvidenceAuthorizationService.Verification verified;
        try {
            verified = authorization.verify(
                    actor,
                    normalizedRequestId,
                    "citation-excerpt",
                    List.of(chunkId));
        } catch (CanonicalEvidenceAuthorizationException denied) {
            throw notFound(
                    actor,
                    chunkId,
                    normalizedRequestId,
                    denied.authorizationModelId(),
                    denied.reasonCode());
        }
        SecureRetrievalCandidate candidate = verified.candidates().getFirst();
        SourceCitationEvidenceResult result = evidenceQuery.findAvailable(
                actor.organizationId(),
                candidate.sourceRevisionId(),
                candidate.knowledgeAssetId());
        SourceCitationEvidence source = switch (result) {
            case SourceCitationEvidenceResult.Available available -> available.evidence();
            case SourceCitationEvidenceResult.Unavailable unavailable -> {
                String reason = switch (unavailable.reason()) {
                    case REVISION_NOT_CURRENT -> "CITATION_EXCERPT_REVISION_NOT_CURRENT";
                    case BLOB_NOT_AVAILABLE -> "CITATION_EXCERPT_BLOB_NOT_AVAILABLE";
                };
                throw notFound(
                        actor,
                        chunkId,
                        normalizedRequestId,
                        verified.authorizationModelId(),
                        reason);
            }
        };
        String content = candidate.content().strip();
        if (content.isEmpty()) {
            throw notFound(
                    actor,
                    chunkId,
                    normalizedRequestId,
                    verified.authorizationModelId(),
                    "CITATION_EXCERPT_EMPTY");
        }
        int codePoints = content.codePointCount(0, content.length());
        boolean truncated = codePoints > MAX_EXCERPT_CODE_POINTS;
        String excerpt = truncated
                ? content.substring(0, content.offsetByCodePoints(0, MAX_EXCERPT_CODE_POINTS))
                : content;
        audit.record(command(
                actor,
                chunkId,
                normalizedRequestId,
                PermissionAuditDecision.ALLOW,
                "AUTHORIZED_CITATION_EXCERPT",
                verified.authorizationModelId(),
                candidate));
        return new CitationEvidenceExcerpt(
                candidate.title(),
                candidate.heading(),
                candidate.startPage(),
                candidate.endPage(),
                excerpt,
                truncated,
                presentationKind(source.fileName()));
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
                "READ_CITATION_EXCERPT",
                "KNOWLEDGE_CHUNK",
                chunkId.toString(),
                PermissionAuditDecision.DENY,
                reason,
                authorizationModelId,
                requestId,
                null));
        return new CitationNotFoundException();
    }

    private static PermissionAuditCommand command(
            CurrentActor actor,
            UUID chunkId,
            String requestId,
            PermissionAuditDecision decision,
            String reason,
            String authorizationModelId,
            SecureRetrievalCandidate candidate) {
        return new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "READ_CITATION_EXCERPT",
                "KNOWLEDGE_CHUNK",
                chunkId.toString(),
                decision,
                reason,
                authorizationModelId,
                requestId,
                null,
                candidate.ingestionAclSnapshotId(),
                candidate.currentAclSnapshotId(),
                candidate.authorizationModelId(),
                candidate.sourceRevisionId(),
                candidate.chunkId(),
                candidate.embeddingProfileId(),
                candidate.projectionGeneration());
    }

    private static CitationPresentationKind presentationKind(String fileName) {
        return KnowledgeContentType.fromFileName(fileName)
                .map(type -> switch (type) {
                    case PDF -> CitationPresentationKind.PDF;
                    case MARKDOWN -> CitationPresentationKind.MARKDOWN;
                    case TEXT -> CitationPresentationKind.PLAIN_TEXT;
                    case PNG, JPEG, GIF, WEBP -> CitationPresentationKind.IMAGE;
                    case WORD, POWERPOINT -> CitationPresentationKind.DOWNLOAD;
                })
                .orElse(CitationPresentationKind.DOWNLOAD);
    }
}
