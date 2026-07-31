package com.orgmemory.core.knowledge.retrieval;

import java.util.UUID;
import java.util.Optional;

public interface QueryEmbeddingPort {

    Optional<QueryEmbedding> embed(UUID organizationId, String query);
}
