package com.orgmemory.graphrag.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RelationContribution(
        UUID id,
        CanonicalRelation relation,
        List<String> keywords,
        String description,
        EvidenceProvenance provenance) {

    public RelationContribution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(relation, "relation");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        if (keywords.stream().anyMatch(keyword -> keyword == null || keyword.isBlank())) {
            throw new IllegalArgumentException("keywords must not contain blank values");
        }
        description = requireText(description, "description");
        Objects.requireNonNull(provenance, "provenance");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
