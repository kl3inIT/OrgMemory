package com.orgmemory.core.knowledge.retrieval;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter-facing registry for resolving immutable embedding profiles without
 * exposing their persistence implementation.
 */
public interface EmbeddingProfileRegistry {

    EmbeddingProfileRef resolve(UUID organizationId, EmbeddingProfileSpec spec);

    EmbeddingProfileRef get(UUID organizationId, UUID profileId);

    Optional<EmbeddingProfileRef> findById(UUID organizationId, UUID profileId);

    Optional<EmbeddingProfileRef> find(UUID organizationId, EmbeddingProfileSpec spec);
}
