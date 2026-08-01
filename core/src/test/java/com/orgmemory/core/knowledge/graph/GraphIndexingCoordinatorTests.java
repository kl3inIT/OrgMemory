package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfile;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRepository;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphChunk;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef;

import com.orgmemory.core.knowledge.acl.SourceAclQuery;
import com.orgmemory.core.knowledge.acl.SourceAclSnapshotRef;

import com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexRevisionRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.LightRagGraphProcessingProfiles;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphIndexingCoordinatorTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID EMBEDDING_PROFILE_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();
    private static final GraphProcessingProfileRef GRAPH_PROCESSING_PROFILE =
            graphProcessingProfile();

    private final GraphIndexJobRepository jobs = mock(GraphIndexJobRepository.class);
    private final KnowledgeAssetGraphQuery assets = mock(KnowledgeAssetGraphQuery.class);
    private final SourceGraphIndexQuery revisions = mock(SourceGraphIndexQuery.class);
    private final SourceAclQuery aclQuery = mock(SourceAclQuery.class);
    private final EmbeddingProfileRepository embeddingProfiles =
            mock(EmbeddingProfileRepository.class);
    private final GraphProcessingProfileRegistry graphProcessingProfiles =
            mock(GraphProcessingProfileRegistry.class);
    private final GraphIndexingCoordinator coordinator = new GraphIndexingCoordinator(
            jobs,
            assets,
            revisions,
            aclQuery,
            embeddingProfiles,
            graphProcessingProfiles);

    private GraphIndexJob job;
    private KnowledgeAssetGraphRef asset;

    @BeforeEach
    void setUpCurrentTarget() {
        job = new GraphIndexJob(
                ORGANIZATION_ID,
                ASSET_ID,
                VERSION_ID,
                REVISION_ID,
                1,
                GRAPH_PROCESSING_PROFILE,
                5,
                Instant.parse("2026-07-23T00:00:00Z"));
        asset = new KnowledgeAssetGraphRef(ASSET_ID, SPACE_ID, VERSION_ID, false);
        KnowledgeAssetVersionGraphRef version = new KnowledgeAssetVersionGraphRef(
                VERSION_ID,
                ASSET_ID,
                REVISION_ID,
                ACL_SNAPSHOT_ID,
                1,
                "vi",
                true);
        SourceGraphIndexRevisionRef revision = new SourceGraphIndexRevisionRef(
                REVISION_ID,
                EMBEDDING_PROFILE_ID,
                ASSET_ID,
                VERSION_ID,
                true);
        SourceAclSnapshotRef snapshot = new SourceAclSnapshotRef(
                ACL_SNAPSHOT_ID,
                null,
                9L,
                null,
                null,
                null,
                null,
                null);
        EmbeddingProfile embeddingProfile = mock(EmbeddingProfile.class);

        when(jobs.lockNextAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(job));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobs.findByIdAndOrganizationId(job.getId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(job));
        when(assets.findAsset(ORGANIZATION_ID, ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(assets.findVersion(ORGANIZATION_ID, VERSION_ID))
                .thenReturn(Optional.of(version));
        when(revisions.findRevision(ORGANIZATION_ID, REVISION_ID))
                .thenReturn(Optional.of(revision));
        when(aclQuery.findSnapshot(ORGANIZATION_ID, ACL_SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshot));
        when(embeddingProfiles.findByIdAndOrganizationId(
                        EMBEDDING_PROFILE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(embeddingProfile));
        when(embeddingProfile.toRef()).thenReturn(new EmbeddingProfileRef(
                EMBEDDING_PROFILE_ID,
                ORGANIZATION_ID,
                "openai/text-embedding-3-large/1536/cosine",
                "openai",
                "text-embedding-3-large",
                1536,
                EmbeddingDistanceMetric.COSINE));
        when(graphProcessingProfiles.get(GRAPH_PROCESSING_PROFILE.id()))
                .thenReturn(GRAPH_PROCESSING_PROFILE);
        when(assets.loadActiveChunks(
                        ORGANIZATION_ID,
                        REVISION_ID,
                        ASSET_ID,
                        VERSION_ID,
                        1))
                .thenReturn(List.of(new KnowledgeAssetGraphChunk(
                        CHUNK_ID,
                        0,
                        "Current chunk",
                        null,
                        2,
                        new FloatVector(new float[1536]))));
    }

    @Test
    void claimsOnlyPinnedCurrentInputsAndRetriesTheSameDurableJob() {
        var claim = coordinator
                .claimNext("worker-a", Duration.ofMinutes(5))
                .orElseThrow();

        assertEquals(VERSION_ID, claim.knowledgeAssetVersionId());
        assertEquals(REVISION_ID, claim.sourceRevisionId());
        assertEquals(ACL_SNAPSHOT_ID, claim.aclSnapshotId());
        assertEquals(9L, claim.aclGeneration());
        assertEquals(CHUNK_ID, claim.chunks().getFirst().id());
        assertEquals("Current chunk", claim.chunks().getFirst().content());
        assertEquals(1536, claim.chunks().getFirst().embedding().dimensions());
        assertEquals(GraphIndexJobStatus.PROCESSING, job.getStatus());

        coordinator.fail(
                job.getId(), "worker-a", "TRANSIENT_PROVIDER", "retry safely");

        assertEquals(GraphIndexJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
    }

    @Test
    void refreshesTheLeaseHeldByTheCurrentWorker() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();
        Instant originalLeaseUntil = job.getLeaseUntil();

        coordinator.refreshLease(job.getId(), "worker-a", Duration.ofHours(1));

        assertTrue(job.getLeaseUntil().isAfter(originalLeaseUntil));
    }

    @Test
    void rejectsLeaseRefreshFromAnotherWorker() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.refreshLease(
                        job.getId(), "worker-b", Duration.ofHours(1)));
    }

    @Test
    void supersedesAClaimWhenTheStableAssetMovesToAnotherVersion() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();
        when(assets.findAsset(ORGANIZATION_ID, ASSET_ID))
                .thenReturn(Optional.of(new KnowledgeAssetGraphRef(
                        ASSET_ID, SPACE_ID, UUID.randomUUID(), false)));

        GraphIndexingStoppedException stopped = assertThrows(
                GraphIndexingStoppedException.class,
                () -> coordinator.complete(job.getId(), "worker-a"));

        assertEquals(
                GraphIndexingStoppedException.Reason.SUPERSEDED,
                stopped.reason());
        assertEquals(GraphIndexJobStatus.SUPERSEDED, job.getStatus());
    }

    @Test
    void heartbeatStopsAndSupersedesBeforeStalePublication() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();
        when(assets.findAsset(ORGANIZATION_ID, ASSET_ID))
                .thenReturn(Optional.of(new KnowledgeAssetGraphRef(
                        ASSET_ID, SPACE_ID, UUID.randomUUID(), false)));

        GraphIndexingStoppedException stopped = assertThrows(
                GraphIndexingStoppedException.class,
                () -> coordinator.refreshLease(
                        job.getId(), "worker-a", Duration.ofMinutes(5)));

        assertEquals(
                GraphIndexingStoppedException.Reason.SUPERSEDED,
                stopped.reason());
        assertEquals(GraphIndexJobStatus.SUPERSEDED, job.getStatus());
    }

    @Test
    void queuedCancellationIsTerminalAndIdempotent() {
        GraphIndexJobView cancelled =
                coordinator.cancel(ORGANIZATION_ID, job.getId());
        GraphIndexJobView replay =
                coordinator.cancel(ORGANIZATION_ID, job.getId());

        assertEquals("CANCELLED", cancelled.status());
        assertEquals(cancelled, replay);
        assertTrue(cancelled.cancellationRequested());
    }

    @Test
    void inFlightCancellationIsAcknowledgedByHeartbeatBeforePublication() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();
        GraphIndexJobView requested =
                coordinator.cancel(ORGANIZATION_ID, job.getId());

        assertEquals("PROCESSING", requested.status());
        assertTrue(requested.cancellationRequested());
        GraphIndexingStoppedException stopped = assertThrows(
                GraphIndexingStoppedException.class,
                () -> coordinator.preparePublication(
                        job.getId(),
                        "worker-a",
                        Duration.ofMinutes(5),
                        "a".repeat(64)));

        assertEquals(
                GraphIndexingStoppedException.Reason.CANCELLED,
                stopped.reason());
        assertEquals(GraphIndexJobStatus.CANCELLED, job.getStatus());
    }

    @Test
    void expiredCancelledJobIsMadeTerminalInsteadOfBlockingTheQueue() {
        job.claim(
                "lost-worker",
                Instant.parse("2026-07-23T00:00:00Z"),
                Duration.ofSeconds(1));
        job.requestCancellation(Instant.parse("2026-07-23T00:00:01Z"));

        assertTrue(coordinator
                .claimNext("worker-b", Duration.ofMinutes(5))
                .isEmpty());
        assertEquals(GraphIndexJobStatus.CANCELLED, job.getStatus());
    }

    @Test
    void failedCurrentJobCanResumeWithFreshRetryBudget() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();
        while (job.getStatus() != GraphIndexJobStatus.FAILED) {
            coordinator.fail(
                    job.getId(),
                    "worker-a",
                    "TRANSIENT",
                    "failure");
            if (job.getStatus() == GraphIndexJobStatus.PENDING) {
                job.claim(
                        "worker-a",
                        Instant.now(),
                        Duration.ofMinutes(5));
            }
        }

        GraphIndexJobView resumed =
                coordinator.resume(ORGANIZATION_ID, job.getId());

        assertEquals("PENDING", resumed.status());
        assertEquals(0, resumed.attempt());
        assertTrue(!resumed.cancellationRequested());
    }

    @Test
    void manifestDriftOnRetryFailsClosed() {
        coordinator.claimNext("worker-a", Duration.ofMinutes(5)).orElseThrow();
        coordinator.preparePublication(
                job.getId(),
                "worker-a",
                Duration.ofMinutes(5),
                "a".repeat(64));

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.preparePublication(
                        job.getId(),
                        "worker-a",
                        Duration.ofMinutes(5),
                        "b".repeat(64)));
    }

    @Test
    void supersedesUnavailableWorkBeforeReturningItToAWorker() {
        when(assets.findAsset(ORGANIZATION_ID, ASSET_ID))
                .thenReturn(Optional.empty());

        assertTrue(coordinator
                .claimNext("worker-a", Duration.ofMinutes(5))
                .isEmpty());
        assertEquals(GraphIndexJobStatus.SUPERSEDED, job.getStatus());
    }

    @Test
    void failsAReclaimedFinalAttemptInsteadOfLeavingItProcessingForever() {
        job = new GraphIndexJob(
                ORGANIZATION_ID,
                ASSET_ID,
                VERSION_ID,
                REVISION_ID,
                1,
                GRAPH_PROCESSING_PROFILE,
                1,
                Instant.parse("2026-07-23T00:00:00Z"));
        job.claim("lost-worker", Instant.parse("2026-07-23T00:00:00Z"), Duration.ofSeconds(1));
        when(jobs.lockNextAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(job));

        assertTrue(coordinator
                .claimNext("worker-b", Duration.ofMinutes(5))
                .isEmpty());
        assertEquals(GraphIndexJobStatus.FAILED, job.getStatus());
    }

    @Test
    void retriesWhenPinnedProjectionInputsAreTemporarilyUnavailable() {
        when(assets.loadActiveChunks(
                        ORGANIZATION_ID,
                        REVISION_ID,
                        ASSET_ID,
                        VERSION_ID,
                        1))
                .thenReturn(List.of());

        assertTrue(coordinator
                .claimNext("worker-a", Duration.ofMinutes(5))
                .isEmpty());
        assertEquals(GraphIndexJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
    }

    @Test
    void processingProfileIsAnIndependentGraphJobIdentityCoordinate() {
        String current = GraphIndexJob.idempotencyKey(
                ORGANIZATION_ID,
                REVISION_ID,
                1,
                "a".repeat(64));
        String rebuilt = GraphIndexJob.idempotencyKey(
                ORGANIZATION_ID,
                REVISION_ID,
                1,
                "b".repeat(64));

        assertTrue(current.startsWith(
                "graph:" + ORGANIZATION_ID + ":" + REVISION_ID + ":1:"));
        assertTrue(!current.equals(rebuilt));
    }

    private static GraphProcessingProfileRef graphProcessingProfile() {
        var profile = LightRagGraphProcessingProfiles.current(new ExtractionProfile(
                "openai",
                "gpt-test",
                LightRagExtractionPrompt.VERSION,
                40,
                60));
        return new GraphProcessingProfileRef(
                UUID.randomUUID(), profile.canonicalSha256(), profile);
    }
}
