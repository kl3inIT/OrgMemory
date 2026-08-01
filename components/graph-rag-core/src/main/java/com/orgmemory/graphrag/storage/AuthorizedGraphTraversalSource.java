package com.orgmemory.graphrag.storage;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot-bound authorized reads used by the core graph traversal policy.
 *
 * <p>Implementations order incident relations by canonical relation UUID and
 * use an exclusive UUID cursor. A next cursor is present only when another page
 * exists and must equal the final relation UUID returned in the current page.
 */
public interface AuthorizedGraphTraversalSource {

    int MAXIMUM_PAGE_SIZE = 10_000;
    Comparator<UUID> CANONICAL_UUID_ORDER = Comparator.comparing(UUID::toString);

    void validateSnapshot(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot);

    List<CanonicalEntity> loadEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds);

    IncidentRelationPage loadIncidentRelationPage(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            UUID afterRelationId,
            int pageSize);

    record IncidentRelationPage(
            List<CanonicalRelation> relations,
            UUID nextCursor) {

        public IncidentRelationPage {
            relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
            UUID previous = null;
            for (CanonicalRelation relation : relations) {
                Objects.requireNonNull(relation, "relations must not contain null");
                if (previous != null
                        && CANONICAL_UUID_ORDER.compare(previous, relation.id()) >= 0) {
                    throw new IllegalArgumentException(
                            "incident relations must be strictly ordered by canonical UUID");
                }
                previous = relation.id();
            }
            if (nextCursor != null
                    && (relations.isEmpty()
                            || !nextCursor.equals(relations.getLast().id()))) {
                throw new IllegalArgumentException(
                        "next cursor must equal the final returned relation UUID");
            }
        }
    }
}
