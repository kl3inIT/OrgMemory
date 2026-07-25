package com.orgmemory.graphrag.model;

import java.util.Objects;
import java.util.UUID;

public record ContributionEmbedding(UUID contributionId, FloatVector vector) {

    public ContributionEmbedding {
        Objects.requireNonNull(contributionId, "contributionId");
        Objects.requireNonNull(vector, "vector");
    }
}
