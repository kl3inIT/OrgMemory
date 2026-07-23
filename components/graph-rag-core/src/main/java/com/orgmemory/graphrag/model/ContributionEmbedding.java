package com.orgmemory.graphrag.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ContributionEmbedding(UUID contributionId, List<Float> vector) {

    public ContributionEmbedding {
        Objects.requireNonNull(contributionId, "contributionId");
        vector = List.copyOf(Objects.requireNonNull(vector, "vector"));
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        if (vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("vector must contain only finite values");
        }
    }
}
