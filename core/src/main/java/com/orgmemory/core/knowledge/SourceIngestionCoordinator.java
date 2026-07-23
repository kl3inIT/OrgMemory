package com.orgmemory.core.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceIngestionCoordinator {

    private final SourceIngestionJobRepository jobs;
    private final SourceRevisionRepository revisions;
    private final SourceObjectRepository sources;
    private final EvidenceBlobRepository blobs;
    private final GraphIndexJobQueue graphIndexJobs;

    SourceIngestionCoordinator(
            SourceIngestionJobRepository jobs,
            SourceRevisionRepository revisions,
            SourceObjectRepository sources,
            EvidenceBlobRepository blobs,
            GraphIndexJobQueue graphIndexJobs) {
        this.jobs = jobs;
        this.revisions = revisions;
        this.sources = sources;
        this.blobs = blobs;
        this.graphIndexJobs = graphIndexJobs;
    }

    @Transactional
    public Optional<ClaimedSourceRevision> claimNext(String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        return jobs.lockNextAvailable(now).map(job -> {
            job.claim(workerId, now, leaseDuration);
            SourceRevision revision = revisions.findById(job.getSourceRevisionId()).orElseThrow();
            SourceObject source = sources.findById(revision.getSourceObjectId()).orElseThrow();
            EvidenceBlob blob = blobs.findById(revision.getEvidenceBlobId()).orElseThrow();
            revision.transitionTo(SourceRevisionStatus.VALIDATING);
            return claimed(job, source, revision, blob);
        });
    }

    @Transactional
    public void markStage(
            UUID jobId,
            String workerId,
            SourceRevisionStatus stage,
            Duration leaseDuration) {
        if (stage == SourceRevisionStatus.READY
                || stage == SourceRevisionStatus.FAILED
                || stage == SourceRevisionStatus.QUARANTINED) {
            throw new IllegalArgumentException("terminal stages require a terminal operation");
        }
        SourceIngestionJob job = claimedJob(jobId, workerId);
        job.refreshLease(Instant.now(), leaseDuration);
        SourceRevision revision = revisions.findById(job.getSourceRevisionId()).orElseThrow();
        revision.transitionTo(stage);
    }

    @Transactional
    public void markEvidenceValidated(UUID jobId, String workerId) {
        SourceIngestionJob job = claimedJob(jobId, workerId);
        SourceRevision revision = revisions.findById(job.getSourceRevisionId()).orElseThrow();
        blobs.findById(revision.getEvidenceBlobId()).orElseThrow().markValidated();
    }

    @Transactional
    public void complete(
            UUID jobId,
            String workerId,
            String pipelineVersion,
            String parserVersion,
            String chunkerVersion,
            DocumentProcessingProfileSnapshot processingProfile,
            EmbeddingProfileRef embeddingProfile,
            RawSourceRef raw,
            NormalizedRecordRef normalized,
            KnowledgeAssetRef asset) {
        SourceIngestionJob job = claimedJob(jobId, workerId);
        SourceRevision revision = revisions.findById(job.getSourceRevisionId()).orElseThrow();
        Instant completedAt = Instant.now();
        revision.ready(
                pipelineVersion,
                parserVersion,
                chunkerVersion,
                processingProfile,
                embeddingProfile,
                raw,
                normalized,
                asset,
                completedAt);
        SourceObject source = sources.findById(revision.getSourceObjectId()).orElseThrow();
        source.publishRevision(revision.getId());
        revisions.saveAndFlush(revision);
        graphIndexJobs.enqueue(
                revision.getOrganizationId(), revision.getId(), asset, completedAt);
        job.succeed();
    }

    @Transactional
    public void fail(
            UUID jobId,
            String workerId,
            String code,
            String message,
            boolean retryable,
            boolean quarantine) {
        SourceIngestionJob job = claimedJob(jobId, workerId);
        SourceRevision revision = revisions.findById(job.getSourceRevisionId()).orElseThrow();
        if (quarantine) {
            blobs.findById(revision.getEvidenceBlobId()).orElseThrow().reject();
            revision.quarantine(code, message);
            job.failPermanently(code, message);
            return;
        }
        if (!retryable) {
            revision.fail(code, message);
            job.failPermanently(code, message);
            return;
        }
        long delaySeconds = Math.min(300, 1L << Math.min(job.getAttemptCount(), 8));
        boolean retryScheduled = job.retry(code, message, Instant.now().plusSeconds(delaySeconds));
        if (retryScheduled) {
            revision.waitForRetry(code, message);
        } else {
            revision.fail(code, message);
        }
    }

    private SourceIngestionJob claimedJob(UUID jobId, String workerId) {
        SourceIngestionJob job = jobs.findById(jobId).orElseThrow();
        if (!job.isClaimedBy(workerId)) {
            throw new IllegalStateException("ingestion job lease is not owned by this worker");
        }
        return job;
    }

    private static ClaimedSourceRevision claimed(
            SourceIngestionJob job,
            SourceObject source,
            SourceRevision revision,
            EvidenceBlob blob) {
        return new ClaimedSourceRevision(
                job.getId(),
                revision.getOrganizationId(),
                revision.getKnowledgeSpaceId(),
                source.getId(),
                revision.getId(),
                blob.getId(),
                revision.getCreatedByUserId(),
                revision.getDepartmentId(),
                source.getSourceConnectionKey(),
                source.getExternalObjectId(),
                revision.getFileName(),
                revision.getMediaType(),
                revision.getContentLength(),
                revision.getContentSha256(),
                blob.getObjectKey(),
                revision.getClassification(),
                revision.getDeclaredAccess(),
                job.getAttemptCount(),
                revision.getCreatedAt());
    }
}
