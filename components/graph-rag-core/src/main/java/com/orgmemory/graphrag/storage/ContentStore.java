package com.orgmemory.graphrag.storage;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface ContentStore extends StagedProjectionWriter {

    @Override
    default ProjectionKind projectionKind() {
        return ProjectionKind.CONTENT;
    }

    void stageUpsert(ProjectionBatch batch, Collection<ContentRecord> records);

    void stageDelete(ProjectionBatch batch, Collection<String> ids);

    Optional<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            String id);

    List<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids);

    record ContentRecord(
            String id,
            EvidenceReference evidence,
            ContentKind kind,
            String content,
            int tokenCount,
            Map<String, String> metadata) {

        public ContentRecord {
            id = requireText(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(kind, "kind");
            content = Objects.requireNonNull(content, "content");
            if (tokenCount < 0) {
                throw new IllegalArgumentException("tokenCount must be non-negative");
            }
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    enum ContentKind {
        DOCUMENT,
        CHUNK,
        ENTITY_CONTRIBUTION,
        RELATION_CONTRIBUTION,
        MULTIMODAL_ANALYSIS
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
