package com.orgmemory.graphrag.storage;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public interface LexicalIndex extends StagedProjectionWriter {

    @Override
    default ProjectionKind projectionKind() {
        return ProjectionKind.LEXICAL;
    }

    void stageUpsert(ProjectionBatch batch, Collection<LexicalDocument> documents);

    void stageDelete(ProjectionBatch batch, Collection<String> ids);

    SearchPage search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            SearchRequest request);

    record LexicalDocument(
            String id,
            EvidenceReference evidence,
            String content,
            Map<String, String> fields) {

        public LexicalDocument {
            id = requireText(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            content = requireText(content, "content");
            fields = Map.copyOf(Objects.requireNonNull(fields, "fields"));
        }
    }

    record SearchRequest(
            String query,
            Set<String> fields,
            int limit,
            double minimumScore,
            String cursor) {

        public SearchRequest {
            query = requireText(query, "query");
            fields = Set.copyOf(Objects.requireNonNull(fields, "fields"));
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
            if (!Double.isFinite(minimumScore)) {
                throw new IllegalArgumentException("minimumScore must be finite");
            }
            cursor = normalizeOptional(cursor);
        }
    }

    record SearchHit(
            String id,
            EvidenceReference evidence,
            double score,
            Map<String, Double> scoreSignals) {

        public SearchHit {
            id = requireText(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("score must be finite");
            }
            scoreSignals = Map.copyOf(Objects.requireNonNull(scoreSignals, "scoreSignals"));
        }
    }

    record SearchPage(List<SearchHit> hits, String nextCursor) {

        public SearchPage {
            hits = List.copyOf(Objects.requireNonNull(hits, "hits"));
            nextCursor = normalizeOptional(nextCursor);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
