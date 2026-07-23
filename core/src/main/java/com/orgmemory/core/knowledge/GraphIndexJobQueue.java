package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Enqueues the rebuildable graph projection only after the canonical source
 * revision has reached READY in the caller's transaction.
 */
@Service
class GraphIndexJobQueue {

    private final GraphIndexJobRepository jobs;
    private final KnowledgeAssetVersionRepository versions;
    private final SourceIngestionProperties ingestionProperties;

    GraphIndexJobQueue(
            GraphIndexJobRepository jobs,
            KnowledgeAssetVersionRepository versions,
            SourceIngestionProperties ingestionProperties) {
        this.jobs = jobs;
        this.versions = versions;
        this.ingestionProperties = ingestionProperties;
    }

    void enqueue(
            UUID organizationId,
            UUID sourceRevisionId,
            KnowledgeAssetRef assetRef,
            Instant availableAt) {
        if (assetRef.status() != KnowledgeAssetVersionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Graph indexing requires an active Knowledge Asset version");
        }
        KnowledgeAssetVersion version = versions
                .findByIdAndOrganizationId(
                        assetRef.knowledgeAssetVersionId(), organizationId)
                .filter(candidate -> candidate.getKnowledgeAssetId()
                        .equals(assetRef.knowledgeAssetId()))
                .filter(candidate -> sourceRevisionId.equals(candidate.getSourceRevisionId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Graph indexing target does not match the active Knowledge Asset version"));
        if (jobs.findByKnowledgeAssetVersionId(version.getId()).isPresent()) {
            return;
        }
        jobs.save(new GraphIndexJob(
                organizationId,
                version.getKnowledgeAssetId(),
                version.getId(),
                sourceRevisionId,
                version.getVersionNumber(),
                ingestionProperties.maximumAttempts(),
                availableAt));
    }
}
