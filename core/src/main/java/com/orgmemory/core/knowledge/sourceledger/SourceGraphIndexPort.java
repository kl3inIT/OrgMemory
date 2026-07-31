package com.orgmemory.core.knowledge.sourceledger;

import java.time.Instant;
import java.util.UUID;

/** Outbound boundary for scheduling a graph projection after source publication. */
public interface SourceGraphIndexPort {

    void enqueue(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            Instant availableAt);
}
