package com.orgmemory.graphrag.export;

import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authorization-filtered graph export input. */
public record GraphExportDocument(
        List<EntityRow> entities,
        List<RelationRow> relations) {

    public GraphExportDocument {
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
    }

    public record EntityRow(
            UUID id,
            String name,
            String type,
            String description,
            List<EvidenceReference> evidence) {

        public EntityRow {
            Objects.requireNonNull(id, "id");
            name = requireText(name, "name");
            type = requireText(type, "type");
            description = requireText(description, "description");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        }
    }

    public record RelationRow(
            UUID id,
            UUID sourceEntityId,
            UUID targetEntityId,
            String type,
            List<String> keywords,
            String description,
            double weight,
            List<EvidenceReference> evidence) {

        public RelationRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceEntityId, "sourceEntityId");
            Objects.requireNonNull(targetEntityId, "targetEntityId");
            type = requireText(type, "type");
            keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
            description = requireText(description, "description");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException(
                        "weight must be finite and positive");
            }
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
