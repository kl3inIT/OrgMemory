package com.orgmemory.graphrag.model;

import java.util.Objects;

public record ExtractionProfile(
        String provider,
        String model,
        String promptVersion,
        int maxEntities,
        int maxRelations) {

    public ExtractionProfile {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        promptVersion = requireText(promptVersion, "promptVersion");
        if (maxEntities <= 0 || maxRelations <= 0) {
            throw new IllegalArgumentException("extraction limits must be positive");
        }
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
