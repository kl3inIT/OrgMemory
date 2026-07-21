package com.orgmemory.core.knowledge;

public record EmbeddingProfileSpec(
        String provider,
        String model,
        int dimensions,
        EmbeddingDistanceMetric distanceMetric) {

    public EmbeddingProfileSpec {
        provider = required(provider, "embedding provider").toLowerCase(java.util.Locale.ROOT);
        model = required(model, "embedding model");
        if (dimensions <= 0 || dimensions > 16000) {
            throw new IllegalArgumentException("embedding dimensions must be between 1 and 16000");
        }
        if (distanceMetric == null) {
            throw new IllegalArgumentException("embedding distance metric is required");
        }
    }

    public String profileKey() {
        return provider + "/" + model + "/" + dimensions + "/"
                + distanceMetric.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
