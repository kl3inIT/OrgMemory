package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only Source Ledger boundary for graph-index revision state. */
@Service
@Transactional(readOnly = true)
public class SourceGraphIndexQuery {

    private final SourceRevisionRepository revisions;

    SourceGraphIndexQuery(SourceRevisionRepository revisions) {
        this.revisions = revisions;
    }

    public Optional<SourceGraphIndexRevisionRef> findRevision(
            UUID organizationId, UUID sourceRevisionId) {
        return revisions.findByIdAndOrganizationId(
                        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId"),
                        Objects.requireNonNull(organizationId, "organizationId"))
                .map(SourceGraphIndexQuery::ref);
    }

    private static SourceGraphIndexRevisionRef ref(SourceRevision revision) {
        return new SourceGraphIndexRevisionRef(
                revision.getId(),
                revision.getEmbeddingProfileId(),
                revision.getKnowledgeAssetId(),
                revision.getKnowledgeAssetVersionId(),
                revision.getStatus() == SourceRevisionStatus.READY);
    }
}
