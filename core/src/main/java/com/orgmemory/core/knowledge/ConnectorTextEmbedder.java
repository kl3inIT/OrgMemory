package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;

/**
 * Turns connector chunk texts into vectors under a resolved, immutable embedding profile.
 * The connector owns chunking and persistence; this port owns only the model call, so the
 * runtime (worker) can back it with a real model while tests supply a deterministic one.
 * Implementations must return one vector per input text, each of the profile's dimension.
 */
public interface ConnectorTextEmbedder {

    ConnectorEmbeddingResult embed(UUID organizationId, List<String> chunkTexts);
}
