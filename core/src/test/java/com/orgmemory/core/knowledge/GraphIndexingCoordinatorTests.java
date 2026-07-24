package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private final GraphIndexJobRepository jobs = mock(GraphIndexJobRepository.class);
    private final KnowledgeAssetRepository assets = mock(KnowledgeAssetRepository.class);
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final SourceAclSnapshotRepository aclSnapshots =
            mock(SourceAclSnapshotRepository.class);
    private final EmbeddingProfileRepository embeddingProfiles =
            mock(EmbeddingProfileRepository.class);
    private final KnowledgeChunkProjectionStore chunks =
            mock(KnowledgeChunkProjectionStore.class);
    private final GraphIndexingCoordinator coordinator = new GraphIndexingCoordinator(
            jobs, assets, versions, revisions, aclSnapshots, embeddingProfiles, chunks);

    private GraphIndexJob job;
    private KnowledgeAsset asset;

    @BeforeEach
    void setUpCurrentTarget() {
        job = new GraphIndexJob(
                ORGANIZATION_ID,
                ASSET_ID,
                VERSION_ID,
                REVISION_ID,
                1,
                5,
                Instant.parse("2026-07-23T00:00:00Z"));
        asset = mock(KnowledgeAsset.class);
        KnowledgeAssetVersion version = mock(KnowledgeAssetVersion.class);
        SourceRevision revision = mock(SourceRevision.class);
        SourceAclSnapshot snapshot = mock(SourceAclSnapshot.class);
        EmbeddingProfile embeddingProfile = mock(EmbeddingProfile.class);

        when(jobs.lockNextAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(job));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobs.findByIdAndOrganizationId(job.getId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(job));
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getCurrentVersionId()).thenReturn(VERSION_ID);
        when(asset.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(versions.findByIdAndOrganizationId(VERSION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(version));
        when(version.getStatus()).thenReturn(KnowledgeAssetVersionStatus.ACTIVE);
        when(version.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(version.getSourceRevisionId()).thenReturn(REVISION_ID);
        when(version.getSourceAclSnapshotId()).thenReturn(ACL_SNAPSHOT_ID);
        when(version.getLanguage()).thenReturn("vi");
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.READY);
        when(revision.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(revision.getKnowledgeAssetVersionId()).thenReturn(VERSION_ID);
        when(revision.getEmbeddingProfileId()).thenReturn(EMBEDDING_PROFILE_ID);
        when(aclSnapshots.findByIdAndOrganizationId(ACL_SNAPSHOT_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(snapshot));
        when(snapshot.getId()).thenReturn(ACL_SNAPSHOT_ID);
        when(snapshot.getAclGeneration()).thenReturn(9L);
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
        when(chunks.loadActive(
                        ORGANIZATION_ID,
                        REVISION_ID,
                        ASSET_ID,
                        VERSION_ID,
                        1))
                .thenReturn(List.of(new GraphIndexChunk(CHUNK_ID, 0, "Current chunk")));
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
        when(asset.getCurrentVersionId()).thenReturn(UUID.randomUUID());

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
        when(asset.getCurrentVersionId()).thenReturn(UUID.randomUUID());

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
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
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
        when(chunks.loadActive(
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
}
