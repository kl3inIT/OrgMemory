package com.orgmemory.graphrag.model;

import java.util.List;
import java.util.Objects;

public record ExtractedRelation(
        String sourceReference,
        String targetReference,
        String type,
        List<String> keywords,
        String description,
        RelationOrientation orientation,
        double weight,
        double confidence) {

    public ExtractedRelation {
        sourceReference = requireText(sourceReference, "sourceReference");
        targetReference = requireText(targetReference, "targetReference");
        type = requireText(type, "type");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
        if (keywords.stream().anyMatch(keyword -> keyword == null || keyword.isBlank())) {
            throw new IllegalArgumentException("keywords must not contain blank values");
        }
        description = requireText(description, "description");
        Objects.requireNonNull(orientation, "orientation");
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("weight must be finite and positive");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    public ExtractedRelation(
            String sourceReference,
            String targetReference,
            String type,
            List<String> keywords,
            String description,
            RelationOrientation orientation,
            double confidence) {
        this(
                sourceReference,
                targetReference,
                type,
                keywords,
                description,
                orientation,
                1.0,
                confidence);
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
