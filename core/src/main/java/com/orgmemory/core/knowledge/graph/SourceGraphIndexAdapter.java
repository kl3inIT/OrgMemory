package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Graph-owned adapter for source-ledger projection scheduling. */
@Service
class SourceGraphIndexAdapter implements SourceGraphIndexPort {

    private final GraphIndexJobQueue jobs;

    SourceGraphIndexAdapter(GraphIndexJobQueue jobs) {
        this.jobs = jobs;
    }

    @Override
    public void enqueue(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            Instant availableAt) {
        jobs.enqueue(
                organizationId,
                sourceRevisionId,
                knowledgeAssetId,
                knowledgeAssetVersionId,
                availableAt);
    }
}
