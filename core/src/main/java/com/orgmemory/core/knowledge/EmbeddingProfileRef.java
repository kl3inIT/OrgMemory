package com.orgmemory.core.knowledge;

import java.util.UUID;

public record EmbeddingProfileRef(
        UUID id,
        UUID organizationId,
        String profileKey,
        String provider,
        String model,
        int dimensions,
        EmbeddingDistanceMetric distanceMetric) {
}
