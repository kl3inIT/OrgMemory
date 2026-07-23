package com.orgmemory.graphrag.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ExtractionResult(
        ExtractionProfile profile,
        List<ExtractedEntity> entities,
        List<ExtractedRelation> relations,
        ExtractionDiagnostics diagnostics) {

    public ExtractionResult {
        Objects.requireNonNull(profile, "profile");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (entities.size() > profile.maximumFinalEntities()
                || relations.size() > profile.maximumFinalRelations()) {
            throw new IllegalArgumentException("extraction result exceeds the configured limits");
        }
        Set<String> entityReferences = new HashSet<>();
        for (ExtractedEntity entity : entities) {
            if (!entityReferences.add(entity.reference())) {
                throw new IllegalArgumentException("entity references must be unique");
            }
        }
        boolean unresolvedRelation = relations.stream().anyMatch(relation ->
                !entityReferences.contains(relation.sourceReference())
                        || !entityReferences.contains(relation.targetReference()));
        if (unresolvedRelation) {
            throw new IllegalArgumentException("every relation endpoint must reference an extracted entity");
        }
    }

    public ExtractionResult(
            ExtractionProfile profile,
            List<ExtractedEntity> entities,
            List<ExtractedRelation> relations) {
        this(profile, entities, relations, ExtractionDiagnostics.notProfiled());
    }
}
