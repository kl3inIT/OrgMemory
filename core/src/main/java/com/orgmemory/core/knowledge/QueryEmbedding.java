package com.orgmemory.core.knowledge;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record QueryEmbedding(UUID profileId, int dimensions, float[] vector) {

    public QueryEmbedding {
        Objects.requireNonNull(profileId, "profileId");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        vector = Arrays.copyOf(Objects.requireNonNull(vector, "vector"), vector.length);
        if (vector.length != dimensions) {
            throw new IllegalArgumentException("embedding dimensions do not match the vector length");
        }
    }

    @Override
    public float[] vector() {
        return Arrays.copyOf(vector, vector.length);
    }
}
