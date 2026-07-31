package com.orgmemory.core.knowledge.graph;

import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record KnowledgeGraphView(
        UUID knowledgeSpaceId,
        long authorizationGeneration,
        boolean canCurate,
        List<Entity> entities,
        List<Relation> relations,
        boolean truncated) {

    public KnowledgeGraphView {
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        if (authorizationGeneration < 0) {
            throw new IllegalArgumentException(
                    "authorizationGeneration must be non-negative");
        }
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
    }

    public record Entity(
            UUID id,
            String name,
            String type,
            String description,
            List<UUID> citationChunkIds,
            EvidenceReference governingEvidence) {

        public Entity {
            Objects.requireNonNull(id, "id");
            name = requireText(name, "name");
            type = requireText(type, "type");
            description = requireText(description, "description");
            citationChunkIds = List.copyOf(
                    Objects.requireNonNull(
                            citationChunkIds,
                            "citationChunkIds"));
        }
    }

    public record Relation(
            UUID id,
            UUID sourceEntityId,
            UUID targetEntityId,
            String type,
            String description,
            double weight,
            List<String> keywords,
            List<UUID> citationChunkIds,
            EvidenceReference governingEvidence) {

        public Relation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceEntityId, "sourceEntityId");
            Objects.requireNonNull(targetEntityId, "targetEntityId");
            type = requireText(type, "type");
            description = requireText(description, "description");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException(
                        "weight must be finite and positive");
            }
            keywords = List.copyOf(
                    Objects.requireNonNull(keywords, "keywords"));
            citationChunkIds = List.copyOf(
                    Objects.requireNonNull(
                            citationChunkIds,
                            "citationChunkIds"));
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
        return normalized;
    }
}
