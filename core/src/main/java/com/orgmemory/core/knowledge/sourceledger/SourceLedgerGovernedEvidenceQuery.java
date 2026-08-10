package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.evidence.GovernedEvidenceQuery;
import com.orgmemory.core.knowledge.evidence.GovernedEvidenceRef;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class SourceLedgerGovernedEvidenceQuery implements GovernedEvidenceQuery {

    private final SourceObjectRepository sources;
    private final SourceRevisionRepository revisions;

    SourceLedgerGovernedEvidenceQuery(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions) {
        this.sources = sources;
        this.revisions = revisions;
    }

    @Override
    public Optional<GovernedEvidenceRef> find(
            UUID organizationId,
            UUID sourceObjectId,
            UUID sourceRevisionId) {
        UUID organization = Objects.requireNonNull(organizationId, "organizationId");
        SourceObject source = sources.findByIdAndOrganizationId(
                        Objects.requireNonNull(sourceObjectId, "sourceObjectId"),
                        organization)
                .orElse(null);
        SourceRevision revision = revisions.findByIdAndOrganizationId(
                        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId"),
                        organization)
                .orElse(null);
        if (source == null
                || revision == null
                || !source.getId().equals(revision.getSourceObjectId())) {
            return Optional.empty();
        }
        return Optional.of(new GovernedEvidenceRef(
                organization,
                source.getKnowledgeSpaceId(),
                source.getId(),
                revision.getId(),
                state(revision.getStatus()),
                source.getStatus() == SourceObjectStatus.ACTIVE,
                revision.getId().equals(source.getLatestRevisionId()),
                revision.getId().equals(source.getCurrentRevisionId()),
                revision.getKnowledgeAssetId(),
                revision.getKnowledgeAssetVersionId(),
                source.getTitle(),
                revision.getFileName(),
                revision.getFailureCode()));
    }

    private static GovernedEvidenceRef.ProcessingState state(SourceRevisionStatus status) {
        return switch (status) {
            case READY -> GovernedEvidenceRef.ProcessingState.READY;
            case FAILED -> GovernedEvidenceRef.ProcessingState.FAILED;
            case QUARANTINED -> GovernedEvidenceRef.ProcessingState.QUARANTINED;
            default -> GovernedEvidenceRef.ProcessingState.PROCESSING;
        };
    }
}
