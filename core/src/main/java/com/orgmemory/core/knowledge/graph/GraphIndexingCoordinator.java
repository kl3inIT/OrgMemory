package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef;

import com.orgmemory.core.knowledge.acl.SourceAclQuery;
import com.orgmemory.core.knowledge.acl.SourceAclSnapshotRef;

import com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexRevisionRef;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionCommitPermit;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;

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
    private final KnowledgeAssetGraphQuery assets;
    private final SourceGraphIndexQuery revisions;
    private final SourceAclQuery aclQuery;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final GraphProcessingProfileRegistry graphProcessingProfiles;

    GraphIndexingCoordinator(
            GraphIndexJobRepository jobs,
            KnowledgeAssetGraphQuery assets,
            SourceGraphIndexQuery revisions,
            SourceAclQuery aclQuery,
            EmbeddingProfileRegistry embeddingProfiles,
            GraphProcessingProfileRegistry graphProcessingProfiles) {
        this.jobs = jobs;
        this.assets = assets;
        this.revisions = revisions;
        this.aclQuery = aclQuery;
        this.embeddingProfiles = embeddingProfiles;
        this.graphProcessingProfiles = graphProcessingProfiles;
    }

    @Transactional
    public Optional<ClaimedGraphIndex> claimNext(String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        Optional<GraphIndexJob> candidate = jobs.lockNextAvailable(now);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        GraphIndexJob job = candidate.get();
        if (job.cancellationRequested()) {
            job.cancel(now);
            return Optional.empty();
        }
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

    @Transactional(noRollbackFor = GraphIndexingStoppedException.class)
    public void refreshLease(UUID jobId, String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        GraphIndexJob job = claimedJob(jobId, workerId, now);
        requireRunnable(job, now);
        job.refreshLease(now, leaseDuration);
    }

    @Transactional(noRollbackFor = GraphIndexingStoppedException.class)
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

    /**
     * Irrevocably authorizes one exact physical publication attempt.
     *
     * <p>A previously issued exact permit is replayable after lease expiry. A new permit is
     * issued only while the caller owns the current claim epoch and the target is still current.
     */
    @Transactional(noRollbackFor = GraphIndexingStoppedException.class)
    public ProjectionCommitPermit issueOrLoadPublicationPermit(
            UUID jobId,
            String workerId,
            long claimEpoch,
            ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        Instant now = Instant.now();
        GraphIndexJob job = jobs.findById(jobId).orElseThrow();
        if (job.hasPublicationPermitFor(batch.id(), batch.manifestFingerprint())) {
            return permit(job, batch.manifestFingerprint());
        }
        job = claimedJob(jobId, workerId, now);
        if (job.getClaimEpoch() != claimEpoch) {
            throw new IllegalStateException("graph publication claim epoch is stale");
        }
        requireRunnable(job, now);
        job.bindManifest(batch.manifestFingerprint());
        UUID permitId = UUID.randomUUID();
        job.issuePublicationPermit(permitId, batch.id(), claimEpoch, now);
        return permit(job, batch.manifestFingerprint());
    }

    @Transactional(readOnly = true)
    public Optional<ProjectionCommitPermit> publicationPermit(UUID jobId) {
        GraphIndexJob job = jobs.findById(Objects.requireNonNull(jobId, "jobId"))
                .orElseThrow();
        return job.getPublicationPermitId() == null
                ? Optional.empty()
                : Optional.of(permit(job, job.getManifestFingerprint()));
    }

    @Transactional(noRollbackFor = GraphIndexingStoppedException.class)
    public void retirePublicationPermit(
            UUID jobId,
            String workerId,
            long claimEpoch,
            ProjectionBatch batch,
            ProjectionDiscardPermit discardPermit) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(discardPermit, "discardPermit")
                .requireAuthorizes(batch);
        Instant now = Instant.now();
        GraphIndexJob job = claimedJob(jobId, workerId, now);
        if (job.getClaimEpoch() != claimEpoch) {
            throw new IllegalStateException("graph publication claim epoch is stale");
        }
        requireRunnable(job, now);
        if (!job.hasPublicationPermitFor(batch.id(), batch.manifestFingerprint())) {
            throw new IllegalStateException(
                    "discard proof does not match the durable graph commit permit");
        }
        job.retirePublicationPermit(batch.id());
    }

    /** Completes exact post-head convergence from durable permit evidence, without a live lease. */
    @Transactional
    public void completePublished(
            UUID jobId,
            String workerId,
            long claimEpoch,
            ProjectionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        GraphIndexJob job = jobs.findById(jobId).orElseThrow();
        if (job.getStatus() == GraphIndexJobStatus.SUCCEEDED) {
            return;
        }
        if (job.getPublicationPermitId() == null) {
            Instant now = Instant.now();
            job = claimedJob(jobId, workerId, now);
            if (job.getClaimEpoch() != claimEpoch) {
                throw new IllegalStateException("graph publication claim epoch is stale");
            }
            requireRunnable(job, now);
            job.bindManifest(snapshot.manifestFingerprint());
            job.issuePublicationPermit(
                    UUID.randomUUID(), snapshot.batchId(), claimEpoch, now);
        }
        if (!job.hasPublicationPermitFor(snapshot.batchId(), snapshot.manifestFingerprint())) {
            throw new IllegalStateException(
                    "published snapshot is not authorized by the durable graph commit permit");
        }
        job.succeed(Instant.now());
    }

    @Transactional(noRollbackFor = GraphIndexingStoppedException.class)
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
        KnowledgeAssetGraphRef asset = assets
                .findAsset(job.getOrganizationId(), job.getKnowledgeAssetId())
                .orElse(null);
        KnowledgeAssetVersionGraphRef version = assets
                .findVersion(job.getOrganizationId(), job.getKnowledgeAssetVersionId())
                .orElse(null);
        SourceGraphIndexRevisionRef revision = revisions
                .findRevision(job.getOrganizationId(), job.getSourceRevisionId())
                .orElse(null);
        if (!isCurrent(job, asset, version, revision)) {
            return Optional.empty();
        }
        SourceAclSnapshotRef snapshot = aclQuery
                .findSnapshot(job.getOrganizationId(), version.sourceAclSnapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Graph index ACL snapshot is missing"));
        EmbeddingProfileRef embeddingProfile = embeddingProfiles
                .findById(job.getOrganizationId(), revision.embeddingProfileId())
                .orElseThrow(() -> new IllegalStateException(
                        "Graph index embedding profile is missing"));
        GraphProcessingProfileRef graphProcessingProfile =
                graphProcessingProfiles.get(job.getGraphProcessingProfileId());
        var activeChunks = assets.loadActiveChunks(
                        job.getOrganizationId(),
                        job.getSourceRevisionId(),
                        job.getKnowledgeAssetId(),
                        job.getKnowledgeAssetVersionId(),
                        job.getProjectionGeneration())
                .stream()
                .map(chunk -> new GraphIndexChunk(
                        chunk.id(),
                        chunk.index(),
                        chunk.content(),
                        chunk.heading(),
                        chunk.tokenCount(),
                        chunk.embedding()))
                .toList();
        if (activeChunks.isEmpty()) {
            throw new IllegalStateException(
                    "Graph index source has no active chunks for the pinned generation");
        }
        return Optional.of(new ClaimedGraphIndex(
                job.getId(),
                job.getOrganizationId(),
                job.getKnowledgeAssetId(),
                asset.knowledgeSpaceId(),
                job.getKnowledgeAssetVersionId(),
                job.getSourceRevisionId(),
                snapshot.id(),
                snapshot.aclGeneration(),
                job.getProjectionGeneration(),
                graphProcessingProfile,
                job.getIdempotencyKey(),
                embeddingProfile,
                version.language(),
                job.getAttemptCount(),
                job.getClaimEpoch(),
                activeChunks));
    }

    private boolean isCurrent(GraphIndexJob job) {
        KnowledgeAssetGraphRef asset = assets
                .findAsset(job.getOrganizationId(), job.getKnowledgeAssetId())
                .orElse(null);
        KnowledgeAssetVersionGraphRef version = assets
                .findVersion(job.getOrganizationId(), job.getKnowledgeAssetVersionId())
                .orElse(null);
        SourceGraphIndexRevisionRef revision = revisions
                .findRevision(job.getOrganizationId(), job.getSourceRevisionId())
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
            KnowledgeAssetGraphRef asset,
            KnowledgeAssetVersionGraphRef version,
            SourceGraphIndexRevisionRef revision) {
        return asset != null
                && !asset.archived()
                && job.getKnowledgeAssetVersionId().equals(asset.currentVersionId())
                && version != null
                && version.active()
                && job.getKnowledgeAssetId().equals(version.knowledgeAssetId())
                && job.getSourceRevisionId().equals(version.sourceRevisionId())
                && revision != null
                && revision.ready()
                && job.getKnowledgeAssetId().equals(revision.knowledgeAssetId())
                && job.getKnowledgeAssetVersionId().equals(revision.knowledgeAssetVersionId());
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

    private GraphIndexJobView view(GraphIndexJob job) {
        GraphProcessingProfileRef profile =
                graphProcessingProfiles.get(job.getGraphProcessingProfileId());
        return new GraphIndexJobView(
                job.getId(),
                job.getKnowledgeAssetId(),
                job.getKnowledgeAssetVersionId(),
                job.getSourceRevisionId(),
                job.getProjectionGeneration(),
                profile.id(),
                profile.canonicalSha256(),
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

    private static ProjectionCommitPermit permit(
            GraphIndexJob job, String manifestFingerprint) {
        return new ProjectionCommitPermit(
                job.getPublicationPermitId(),
                job.getPublicationPermitBatchId(),
                manifestFingerprint,
                Objects.requireNonNull(job.getPublicationPermitClaimEpoch()),
                job.getPublicationPermitIssuedAt());
    }
}
