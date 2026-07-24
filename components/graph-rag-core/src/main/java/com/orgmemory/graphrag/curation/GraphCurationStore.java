package com.orgmemory.graphrag.curation;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only curation ledger. Duplicate idempotency keys with identical
 * canonical content return the original record; conflicting reuse fails.
 */
public interface GraphCurationStore extends GraphCurationOverlay {

    GraphCurationRecord append(String idempotencyKey, GraphCurationRecord record);

    void deactivate(
            ProjectionNamespace namespace,
            UUID recordId,
            CurationProvenance provenance);

    List<GraphCurationRecord> active(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace);

    @Override
    default List<GraphCurationRecord> load(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace) {
        GraphCurationOverlay.requireMatchingScope(scope, namespace);
        return active(scope, namespace);
    }

    final class CurationConflictException extends RuntimeException {

        public CurationConflictException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }
    }
}
