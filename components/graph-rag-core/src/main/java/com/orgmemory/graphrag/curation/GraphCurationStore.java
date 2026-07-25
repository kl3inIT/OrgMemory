package com.orgmemory.graphrag.curation;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
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
            AuthorizedEvidenceScope scope,
            ProjectionNamespace namespace,
            ProjectionSnapshot snapshot);

    @Override
    default List<GraphCurationRecord> load(
            AuthorizedEvidenceScope scope,
            ProjectionNamespace namespace,
            ProjectionSnapshot snapshot) {
        GraphCurationOverlay.requireMatchingScope(scope, namespace);
        Objects.requireNonNull(snapshot, "snapshot");
        if (!namespace.equals(snapshot.namespace())) {
            throw new IllegalArgumentException(
                    "curation namespace and publication snapshot must match");
        }
        return active(scope, namespace, snapshot);
    }

    final class CurationConflictException extends RuntimeException {

        public CurationConflictException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }
    }
}
