package com.orgmemory.graphrag.storage;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public interface VectorIndex extends StagedProjectionWriter {

    @Override
    default ProjectionKind projectionKind() {
        return ProjectionKind.VECTOR;
    }

    void stageUpsert(ProjectionBatch batch, Collection<VectorRecord> records);

    void stageDelete(ProjectionBatch batch, Collection<String> ids);

    List<VectorRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids);

    List<VectorHit> search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            SearchRequest request);

    record VectorRecord(
            String id,
            EvidenceReference evidence,
            VectorKind kind,
            UUID embeddingProfileId,
            String model,
            FloatVector vector,
            Map<String, String> metadata) {

        public VectorRecord {
            id = requireText(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
            model = requireText(model, "model");
            Objects.requireNonNull(vector, "vector");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    record SearchRequest(
            UUID embeddingProfileId,
            Set<VectorKind> kinds,
            FloatVector queryVector,
            int limit,
            double minimumSimilarity) {

        public SearchRequest {
            Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
            kinds = Set.copyOf(Objects.requireNonNull(kinds, "kinds"));
            if (kinds.isEmpty()) {
                throw new IllegalArgumentException("kinds must not be empty");
            }
            Objects.requireNonNull(queryVector, "queryVector");
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

        public int dimensions() {
            return queryVector.dimensions();
        }
    }

    record VectorHit(
            String id,
            EvidenceReference evidence,
            VectorKind kind,
            double similarity) {

        public VectorHit {
            id = requireText(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(similarity) || similarity < -1.0 || similarity > 1.0) {
                throw new IllegalArgumentException("similarity must be between -1 and 1");
            }
        }
    }

    enum VectorKind {
        CHUNK,
        ENTITY,
        RELATION
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
