package com.orgmemory.graphrag.storage;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot-pinned graph projection boundary shared by every storage adapter.
 *
 * <p>The graph store owns canonical topology and evidence-scoped contributions.
 * Entity and relation embeddings remain in {@link VectorIndex}; this keeps graph
 * storage replaceable without changing query semantics.
 */
public interface GraphStore extends StagedProjectionWriter {

    @Override
    default ProjectionKind projectionKind() {
        return ProjectionKind.GRAPH;
    }

    void stageReplaceRevision(
            ProjectionBatch batch,
            GraphRevisionContributions contributions);

    void stageDeleteRevision(ProjectionBatch batch, UUID sourceRevisionId);

    List<CanonicalEntity> loadEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds);

    List<CanonicalRelation> loadRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds);

    List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds);

    List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds);

    List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit);

    Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds);

    Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds);

    List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit);
}
