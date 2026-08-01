package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef;

import com.orgmemory.core.knowledge.sourceledger.SourceIngestionProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Enqueues the rebuildable graph projection only after the canonical source
 * revision has reached READY in the caller's transaction.
 */
@Service
public class GraphIndexJobQueue {

    private final GraphIndexJobRepository jobs;
    private final KnowledgeAssetGraphQuery assets;
    private final SourceIngestionProperties ingestionProperties;
    private final GraphProcessingProfileResolver processingProfiles;
    private final GraphProcessingProfileRegistry profileRegistry;
    private final JdbcClient jdbc;

    GraphIndexJobQueue(
            GraphIndexJobRepository jobs,
            KnowledgeAssetGraphQuery assets,
            SourceIngestionProperties ingestionProperties,
            GraphProcessingProfileResolver processingProfiles,
            GraphProcessingProfileRegistry profileRegistry,
            JdbcClient jdbc) {
        this.jobs = jobs;
        this.assets = assets;
        this.ingestionProperties = ingestionProperties;
        this.processingProfiles = processingProfiles;
        this.profileRegistry = profileRegistry;
        this.jdbc = jdbc;
    }

    public UUID enqueue(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            Instant availableAt) {
        KnowledgeAssetVersionGraphRef version = assets
                .findVersion(organizationId, knowledgeAssetVersionId)
                .filter(candidate -> candidate.knowledgeAssetId()
                        .equals(knowledgeAssetId))
                .filter(candidate -> sourceRevisionId.equals(candidate.sourceRevisionId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Graph indexing target does not match the active Knowledge Asset version"));
        if (!version.active()) {
            throw new IllegalStateException(
                    "Graph indexing requires an active Knowledge Asset version");
        }
        GraphProcessingProfileRef profile =
                profileRegistry.resolve(processingProfiles.current());
        String idempotencyKey = GraphIndexJob.idempotencyKey(
                organizationId,
                sourceRevisionId,
                version.versionNumber(),
                profile.canonicalSha256());
        UUID jobId = UUID.nameUUIDFromBytes(
                idempotencyKey.getBytes(StandardCharsets.UTF_8));
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO graph_index_jobs (
                            id, organization_id, knowledge_asset_id,
                            knowledge_asset_version_id, source_revision_id,
                            graph_processing_profile_id, projection_generation,
                            job_type, status, available_at, attempt_count,
                            max_attempts, idempotency_key, cancellation_requested,
                            created_at, updated_at, version
                        ) VALUES (
                            :id, :organizationId, :knowledgeAssetId,
                            :knowledgeAssetVersionId, :sourceRevisionId,
                            :graphProcessingProfileId, :projectionGeneration,
                            :jobType, 'PENDING', :availableAt, 0,
                            :maxAttempts, :idempotencyKey, false,
                            :createdAt, :createdAt, 0
                        )
                        ON CONFLICT (
                            knowledge_asset_version_id,
                            graph_processing_profile_id
                        ) DO NOTHING
                        """)
                .param("id", jobId)
                .param("organizationId", organizationId)
                .param("knowledgeAssetId", version.knowledgeAssetId())
                .param("knowledgeAssetVersionId", version.id())
                .param("sourceRevisionId", sourceRevisionId)
                .param("graphProcessingProfileId", profile.id())
                .param("projectionGeneration", version.versionNumber())
                .param("jobType", GraphIndexJob.TYPE)
                .param(
                        "availableAt",
                        OffsetDateTime.ofInstant(availableAt, ZoneOffset.UTC))
                .param("maxAttempts", ingestionProperties.maximumAttempts())
                .param("idempotencyKey", idempotencyKey)
                .param("createdAt", createdAt)
                .update();
        return jobs.findByKnowledgeAssetVersionIdAndGraphProcessingProfileId(
                        version.id(), profile.id())
                .orElseThrow(() -> new IllegalStateException(
                        "graph index job enqueue did not produce a durable job"))
                .getId();
    }
}
