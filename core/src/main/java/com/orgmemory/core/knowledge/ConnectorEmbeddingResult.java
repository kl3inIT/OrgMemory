package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * The result of embedding connector chunk texts: the immutable profile the vectors were
 * produced under, and one vector per input text. The profile must already be registered so
 * chunk projections can reference it; every vector length must equal the profile dimension.
 */
public record ConnectorEmbeddingResult(EmbeddingProfileRef profile, List<float[]> vectors) {

    public ConnectorEmbeddingResult {
        if (profile == null) {
            throw new IllegalArgumentException("embedding profile is required");
        }
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("at least one embedding vector is required");
        }
        vectors = vectors.stream().map(float[]::clone).toList();
        for (float[] vector : vectors) {
            if (vector.length != profile.dimensions()) {
                throw new IllegalArgumentException(
                        "embedding vector length does not match the profile dimension");
            }
        }
    }

    @Override
    public List<float[]> vectors() {
        return vectors.stream().map(float[]::clone).toList();
    }
}
