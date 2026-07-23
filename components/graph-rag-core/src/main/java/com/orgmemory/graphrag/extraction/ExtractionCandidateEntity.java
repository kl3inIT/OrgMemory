package com.orgmemory.graphrag.extraction;

import java.util.Objects;

public record ExtractionCandidateEntity(
        String name,
        String type,
        String description,
        double confidence) {

    public ExtractionCandidateEntity {
        name = requireText(name, "name");
        type = requireText(type, "type");
        description = requireText(description, "description");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
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
