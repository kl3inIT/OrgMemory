package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot-pinned, permission-scoped read boundary for the query engine.
 *
 * <p>PR 8 implements this contract over PostgreSQL graph, vector and content
 * projections after they share one namespace snapshot.
 */
public interface AuthorizedQueryProjection {

    List<RankedItem<CanonicalEntity>> searchEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search);

    List<RankedItem<CanonicalRelation>> searchRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search);

    List<RankedItem<Chunk>> searchChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search);

    List<RankedItem<Chunk>> rankChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search,
            Collection<UUID> candidateChunkIds);

    List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit);

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

    List<Chunk> loadChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> chunkIds);

    record VectorSearch(
            UUID embeddingProfileId,
            int embeddingDimensions,
            FloatVector vector,
            int limit,
            double minimumSimilarity) {

        public VectorSearch {
            Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
            Objects.requireNonNull(vector, "vector");
            if (embeddingDimensions <= 0 || vector.dimensions() != embeddingDimensions) {
                throw new IllegalArgumentException("embedding dimensions must match the vector");
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
            if (!Double.isFinite(minimumSimilarity)
                    || minimumSimilarity < -1.0
                    || minimumSimilarity > 1.0) {
                throw new IllegalArgumentException(
                        "minimumSimilarity must be between -1 and 1");
            }
        }
    }

    record Chunk(
            UUID id,
            EvidenceReference evidence,
            String content,
            int tokenCount,
            Map<String, String> metadata) {

        public Chunk {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            if (!id.equals(evidence.chunkId())) {
                throw new IllegalArgumentException("chunk identity must match its evidence");
            }
            content = Objects.requireNonNull(content, "content");
            if (tokenCount < 0) {
                throw new IllegalArgumentException("tokenCount must be non-negative");
            }
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }
}
