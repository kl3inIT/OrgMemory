package com.orgmemory.graphrag.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RelationContribution(
        UUID id,
        CanonicalRelation relation,
        String type,
        List<String> keywords,
        String description,
        double weight,
        EvidenceProvenance provenance) {

    public RelationContribution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(relation, "relation");
        type = requireText(type, "type");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        if (keywords.stream().anyMatch(keyword -> keyword == null || keyword.isBlank())) {
            throw new IllegalArgumentException("keywords must not contain blank values");
        }
        description = requireText(description, "description");
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("weight must be finite and positive");
        }
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
