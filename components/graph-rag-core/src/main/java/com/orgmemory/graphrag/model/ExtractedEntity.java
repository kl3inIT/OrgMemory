package com.orgmemory.graphrag.model;

import java.util.Objects;

public record ExtractedEntity(
        String reference,
        String name,
        String type,
        String description,
        double confidence) {

    public ExtractedEntity {
        reference = requireText(reference, "reference");
        name = requireText(name, "name");
        type = requireText(type, "type");
        description = requireText(description, "description");
        requireConfidence(confidence);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static void requireConfidence(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }
}
