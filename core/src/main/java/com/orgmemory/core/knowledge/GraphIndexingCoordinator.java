package com.orgmemory.core.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphIndexingCoordinator {

    private final GraphIndexJobRepository jobs;
    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;
    private final SourceRevisionRepository revisions;
    private final SourceAclSnapshotRepository aclSnapshots;
    private final EmbeddingProfileRepository embeddingProfiles;
    private final KnowledgeChunkProjectionStore chunks;

    GraphIndexingCoordinator(
            GraphIndexJobRepository jobs,
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions,
            SourceRevisionRepository revisions,
            SourceAclSnapshotRepository aclSnapshots,
            EmbeddingProfileRepository embeddingProfiles,
            KnowledgeChunkProjectionStore chunks) {
        this.jobs = jobs;
        this.assets = assets;
        this.versions = versions;
        this.revisions = revisions;
        this.aclSnapshots = aclSnapshots;
        this.embeddingProfiles = embeddingProfiles;
        this.chunks = chunks;
    }

    @Transactional
    public Optional<ClaimedGraphIndex> claimNext(String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        Optional<GraphIndexJob> candidate = jobs.lockNextAvailable(now);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        GraphIndexJob job = candidate.get();
        if (job.getStatus() == GraphIndexJobStatus.PROCESSING
                && !job.hasAttemptsRemaining()) {
            job.failExpiredLease(now);
            return Optional.empty();
        }
        job.claim(workerId, now, leaseDuration);
        Optional<ClaimedGraphIndex> claim;
        try {
            claim = currentClaim(job);
        } catch (IllegalStateException invalidInput) {
            retry(
                    job,
                    "GRAPH_INPUT_UNAVAILABLE",
                    "Pinned graph indexing input is unavailable",
                    now);
            return Optional.empty();
        }
        if (claim.isEmpty()) {
            job.supersede(now);
        }
        return claim;
    }

    @Transactional
    public void refreshLease(UUID jobId, String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        GraphIndexJob job = claimedJob(jobId, workerId, now);
        requireRunnable(job, now);
        job.refreshLease(now, leaseDuration);
    }

    @Transactional
    public void preparePublication(
            UUID jobId,
            String workerId,
            Duration leaseDuration,
            String manifestFingerprint) {
        Instant now = Instant.now();
        GraphIndexJob job = claimedJob(jobId, workerId, now);
        requireRunnable(job, now);
        job.bindManifest(manifestFingerprint);
        job.refreshLease(now, leaseDuration);
    }

    @Transactional
    public void complete(UUID jobId, String workerId) {
        Instant now = Instant.now();
        GraphIndexJob job = claimedJob(jobId, workerId, now);
        requireRunnable(job, now);
        job.succeed(now);
    }

    @Transactional
    public void fail(UUID jobId, String workerId, String code, String message) {
        Instant now = Instant.now();
        GraphIndexJob job = claimedJob(jobId, workerId, now);
        if (!isCurrent(job)) {
            job.supersede(now);
            return;
        }
        retry(job, code, message, now);
    }

    @Transactional
    public GraphIndexJobView cancel(UUID organizationId, UUID jobId) {
        GraphIndexJob job = tenantJob(organizationId, jobId);
        job.requestCancellation(Instant.now());
        return view(job);
    }

    @Transactional
    public GraphIndexJobView resume(UUID organizationId, UUID jobId) {
        GraphIndexJob job = tenantJob(organizationId, jobId);
        if (!isCurrent(job)) {
            throw new IllegalStateException(
                    "only the current active Knowledge Asset version can rebuild");
        }
        job.resume(Instant.now());
        return view(job);
    }

    @Transactional(readOnly = true)
    public GraphIndexJobView status(UUID organizationId, UUID jobId) {
        return view(tenantJob(organizationId, jobId));
    }

    private Optional<ClaimedGraphIndex> currentClaim(GraphIndexJob job) {
        KnowledgeAsset asset = assets
                .findByIdAndOrganizationId(job.getKnowledgeAssetId(), job.getOrganizationId())
                .orElse(null);
        KnowledgeAssetVersion version = versions
                .findByIdAndOrganizationId(
                        job.getKnowledgeAssetVersionId(), job.getOrganizationId())
                .orElse(null);
        SourceRevision revision = revisions
                .findByIdAndOrganizationId(job.getSourceRevisionId(), job.getOrganizationId())
                .orElse(null);
        if (!isCurrent(job, asset, version, revision)) {
            return Optional.empty();
        }
        SourceAclSnapshot snapshot = aclSnapshots
                .findByIdAndOrganizationId(
                        version.getSourceAclSnapshotId(), job.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Graph index ACL snapshot is missing"));
        EmbeddingProfileRef embeddingProfile = embeddingProfiles
                .findByIdAndOrganizationId(
                        revision.getEmbeddingProfileId(), job.getOrganizationId())
                .map(EmbeddingProfile::toRef)
                .orElseThrow(() -> new IllegalStateException(
                        "Graph index embedding profile is missing"));
        var activeChunks = chunks.loadActive(
                job.getOrganizationId(),
                job.getSourceRevisionId(),
                job.getKnowledgeAssetId(),
                job.getKnowledgeAssetVersionId(),
                job.getProjectionGeneration());
        if (activeChunks.isEmpty()) {
            throw new IllegalStateException(
                    "Graph index source has no active chunks for the pinned generation");
        }
        return Optional.of(new ClaimedGraphIndex(
                job.getId(),
                job.getOrganizationId(),
                job.getKnowledgeAssetId(),
                asset.getKnowledgeSpaceId(),
                job.getKnowledgeAssetVersionId(),
                job.getSourceRevisionId(),
                snapshot.getId(),
                snapshot.getAclGeneration(),
                job.getProjectionGeneration(),
                embeddingProfile,
                version.getLanguage(),
                job.getAttemptCount(),
                activeChunks));
    }

    private boolean isCurrent(GraphIndexJob job) {
        KnowledgeAsset asset = assets
                .findByIdAndOrganizationId(job.getKnowledgeAssetId(), job.getOrganizationId())
                .orElse(null);
        KnowledgeAssetVersion version = versions
                .findByIdAndOrganizationId(
                        job.getKnowledgeAssetVersionId(), job.getOrganizationId())
                .orElse(null);
        SourceRevision revision = revisions
                .findByIdAndOrganizationId(job.getSourceRevisionId(), job.getOrganizationId())
                .orElse(null);
        return isCurrent(job, asset, version, revision);
    }

    private void requireRunnable(GraphIndexJob job, Instant now) {
        if (job.cancellationRequested()) {
            job.cancel(now);
            throw new GraphIndexingStoppedException(
                    GraphIndexingStoppedException.Reason.CANCELLED,
                    "graph indexing was cancelled before publication");
        }
        if (!isCurrent(job)) {
            job.supersede(now);
            throw new GraphIndexingStoppedException(
                    GraphIndexingStoppedException.Reason.SUPERSEDED,
                    "graph indexing target is no longer current");
        }
    }

    private static boolean isCurrent(
            GraphIndexJob job,
            KnowledgeAsset asset,
            KnowledgeAssetVersion version,
            SourceRevision revision) {
        return asset != null
                && asset.getArchivedAt() == null
                && job.getKnowledgeAssetVersionId().equals(asset.getCurrentVersionId())
                && version != null
                && version.getStatus() == KnowledgeAssetVersionStatus.ACTIVE
                && job.getKnowledgeAssetId().equals(version.getKnowledgeAssetId())
                && job.getSourceRevisionId().equals(version.getSourceRevisionId())
                && revision != null
                && revision.getStatus() == SourceRevisionStatus.READY
                && job.getKnowledgeAssetId().equals(revision.getKnowledgeAssetId())
                && job.getKnowledgeAssetVersionId().equals(revision.getKnowledgeAssetVersionId());
    }

    private GraphIndexJob claimedJob(UUID jobId, String workerId, Instant now) {
        GraphIndexJob job = jobs.findById(jobId).orElseThrow();
        if (!job.isClaimedBy(workerId)) {
            throw new IllegalStateException(
                    "graph index job lease is not owned by this worker");
        }
        if (!job.getLeaseUntil().isAfter(now)) {
            throw new IllegalStateException("graph index job lease has expired");
        }
        return job;
    }

    private GraphIndexJob tenantJob(UUID organizationId, UUID jobId) {
        return jobs.findByIdAndOrganizationId(
                        Objects.requireNonNull(jobId, "jobId"),
                        Objects.requireNonNull(organizationId, "organizationId"))
                .orElseThrow();
    }

    private static GraphIndexJobView view(GraphIndexJob job) {
        return new GraphIndexJobView(
                job.getId(),
                job.getKnowledgeAssetId(),
                job.getKnowledgeAssetVersionId(),
                job.getSourceRevisionId(),
                job.getProjectionGeneration(),
                job.getStatus().name(),
                job.getAttemptCount(),
                job.cancellationRequested(),
                job.getCancellationRequestedAt(),
                job.getLastErrorCode(),
                job.getLastErrorMessage(),
                job.getCompletedAt());
    }

    private static void retry(
            GraphIndexJob job, String code, String message, Instant now) {
        long delaySeconds = Math.min(300, 1L << Math.min(job.getAttemptCount(), 8));
        job.retry(code, message, now, now.plusSeconds(delaySeconds));
    }
}
