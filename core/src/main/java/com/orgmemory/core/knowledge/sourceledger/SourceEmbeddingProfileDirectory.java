package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Looks up display metadata for an embedding profile referenced by a source revision. */
public interface SourceEmbeddingProfileDirectory {

    SourceEmbeddingProfileView get(UUID organizationId, UUID profileId);
}
