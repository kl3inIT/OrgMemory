package com.orgmemory.core.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "embedding_profiles")
class EmbeddingProfile {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "profile_key", nullable = false, updatable = false)
    private String profileKey;

    @Column(nullable = false, length = 64, updatable = false)
    private String provider;

    @Column(nullable = false, length = 128, updatable = false)
    private String model;

    @Column(nullable = false, updatable = false)
    private int dimensions;

    @Enumerated(EnumType.STRING)
    @Column(name = "distance_metric", nullable = false, length = 32, updatable = false)
    private EmbeddingDistanceMetric distanceMetric;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmbeddingProfile() {
    }

    EmbeddingProfileRef toRef() {
        return new EmbeddingProfileRef(
                id,
                organizationId,
                profileKey,
                provider,
                model,
                dimensions,
                distanceMetric);
    }
}
