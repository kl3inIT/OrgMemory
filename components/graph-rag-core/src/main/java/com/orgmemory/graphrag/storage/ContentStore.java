package com.orgmemory.graphrag.storage;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface ContentStore extends StagedProjectionWriter {

    String ASSET_PROJECTION_GENERATION_METADATA_KEY =
            "assetProjectionGeneration";

    @Override
    default ProjectionKind projectionKind() {
        return ProjectionKind.CONTENT;
    }

    void stageUpsert(ProjectionBatch batch, Collection<ContentRecord> records);

    void stageDelete(ProjectionBatch batch, Collection<String> ids);

    /** Removes every copied-forward record for one stable Knowledge Asset. */
    void stageDeleteAsset(ProjectionBatch batch, UUID knowledgeAssetId);

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

        /**
         * Returns the immutable Knowledge Asset version generation represented
         * by this record. This is intentionally distinct from the projection
         * snapshot generation that copied the record forward.
         */
        public long assetProjectionGeneration() {
            String raw = metadata.get(ASSET_PROJECTION_GENERATION_METADATA_KEY);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException(
                        "content record is missing asset projection generation");
            }
            final long generation;
            try {
                generation = Long.parseLong(raw);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                        "content record has an invalid asset projection generation",
                        exception);
            }
            if (generation <= 0) {
                throw new IllegalStateException(
                        "content record has a non-positive asset projection generation");
            }
            return generation;
        }
    }

    enum ContentKind {
        DOCUMENT,
        CHUNK,
        ENTITY_CONTRIBUTION,
        RELATION_CONTRIBUTION,
        MULTIMODAL_ANALYSIS
    }

}
