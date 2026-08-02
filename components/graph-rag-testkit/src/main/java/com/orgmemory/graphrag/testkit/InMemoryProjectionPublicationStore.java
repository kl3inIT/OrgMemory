package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionAbortOutcome;
import com.orgmemory.graphrag.storage.ProjectionCommitPermit;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationNotReadyException;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic reference implementation for publication conformance tests.
 */
public final class InMemoryProjectionPublicationStore
        implements ProjectionPublicationStore {

    private final Map<ProjectionNamespace, ProjectionSnapshot> heads = new HashMap<>();
    private final Map<ProjectionGeneration, ProjectionSnapshot> publicationHistory =
            new HashMap<>();
    private final Map<UUID, ProjectionSnapshot> publishedBatches = new HashMap<>();
    private final Map<IdempotencyKey, ProjectionSnapshot> idempotentPublications =
            new HashMap<>();
    private final Map<UUID, ProjectionBatch> registeredBatches = new HashMap<>();
    private final Map<IdempotencyKey, UUID> registeredIdempotencyKeys = new HashMap<>();
    private final Map<UUID, Map<ProjectionKind, Instant>> preparationReceipts =
            new HashMap<>();
    private final Map<UUID, ProjectionCommitPermit> commitPermits = new HashMap<>();
    private final Set<UUID> abortedBatches = new HashSet<>();

    @Override
    public synchronized ProjectionBatch begin(ProjectionBatch candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ProjectionBatch exact = registeredBatches.get(candidate.id());
        if (exact != null) {
            requireSameBatchIdentity(candidate, exact);
            return exact;
        }
        IdempotencyKey key = new IdempotencyKey(
                candidate.namespace(), candidate.idempotencyKey());
        UUID activeId = registeredIdempotencyKeys.get(key);
        if (activeId != null) {
            ProjectionBatch active = registeredBatches.get(activeId);
            requireSameOperation(candidate, active);
            if (commitPermits.containsKey(active.id())) {
                return active;
            }
            if (candidate.claimEpoch() <= 0
                    || candidate.claimEpoch() <= active.claimEpoch()) {
                throw new PublicationConflictException(
                        "a live or unfenced publication attempt already owns the operation");
            }
            abortedBatches.add(active.id());
            registeredIdempotencyKeys.remove(key, active.id());
        }
        register(candidate);
        return registeredBatches.get(candidate.id());
    }

    @Override
    public synchronized boolean hasBoundCommitPermit(ProjectionBatch batch) {
        ProjectionBatch registered = registeredBatches.get(batch.id());
        if (registered == null) {
            return false;
        }
        requireSameBatchIdentity(batch, registered);
        return commitPermits.containsKey(batch.id());
    }

    @Override
    public synchronized Optional<ProjectionSnapshot> current(ProjectionNamespace namespace) {
        return Optional.ofNullable(heads.get(Objects.requireNonNull(namespace, "namespace")));
    }

    @Override
    public synchronized Optional<ProjectionSnapshot> published(
            ProjectionNamespace namespace,
            long generation) {
        Objects.requireNonNull(namespace, "namespace");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        return Optional.ofNullable(
                publicationHistory.get(new ProjectionGeneration(namespace, generation)));
    }

    @Override
    public synchronized Optional<ProjectionSnapshot> published(
            ProjectionNamespace namespace,
            String idempotencyKey) {
        return Optional.ofNullable(idempotentPublications.get(
                new IdempotencyKey(
                        Objects.requireNonNull(namespace, "namespace"),
                        requireText(idempotencyKey, "idempotencyKey"))));
    }

    @Override
    public synchronized void markPrepared(
            ProjectionBatch batch,
            ProjectionKind projection,
            Instant preparedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(preparedAt, "preparedAt");
        if (!batch.requiredProjections().contains(projection)) {
            throw new IllegalArgumentException(
                    "projection is not required by this batch");
        }
        if (abortedBatches.contains(batch.id())) {
            throw new PublicationConflictException("an aborted batch cannot be prepared");
        }
        register(batch);
        preparationReceipts
                .computeIfAbsent(batch.id(), ignored -> new HashMap<>())
                .putIfAbsent(projection, preparedAt);
    }

    @Override
    public synchronized ProjectionSnapshot publish(
            ProjectionBatch batch,
            ProjectionCommitPermit permit,
            Instant publishedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(permit, "permit").requireAuthorizes(batch);
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (abortedBatches.contains(batch.id())) {
            throw new PublicationConflictException("an aborted batch cannot be published");
        }
        ProjectionCommitPermit existingPermit = commitPermits.get(batch.id());
        if (existingPermit != null
                && (!existingPermit.id().equals(permit.id())
                        || existingPermit.claimEpoch() != permit.claimEpoch())) {
            throw new PublicationConflictException(
                    "publication attempt is bound to a different commit permit");
        }
        ProjectionSnapshot replay = publishedBatches.get(batch.id());
        if (replay != null) {
            requireSameBatchIdentity(batch, registeredBatches.get(batch.id()));
            requireSamePublication(batch, replay);
            return replay;
        }

        IdempotencyKey idempotencyKey =
                new IdempotencyKey(batch.namespace(), batch.idempotencyKey());
        ProjectionSnapshot idempotentReplay = idempotentPublications.get(idempotencyKey);
        if (idempotentReplay != null) {
            requireSamePublication(batch, idempotentReplay);
            return idempotentReplay;
        }

        register(batch);
        Set<ProjectionKind> prepared =
                preparationReceipts
                        .getOrDefault(batch.id(), Map.of())
                        .keySet();
        if (!prepared.equals(batch.requiredProjections())) {
            throw new PublicationNotReadyException(
                    "every required projection must have a durable preparation receipt");
        }
        commitPermits.putIfAbsent(batch.id(), permit);

        long currentGeneration = Optional.ofNullable(heads.get(batch.namespace()))
                .map(ProjectionSnapshot::generation)
                .orElse(0L);
        if (currentGeneration != batch.expectedPreviousGeneration()) {
            throw new PublicationConflictException(
                    "expected generation "
                            + batch.expectedPreviousGeneration()
                            + " but current generation is "
                            + currentGeneration);
        }
        UUID currentBatchId = Optional.ofNullable(heads.get(batch.namespace()))
                .map(ProjectionSnapshot::batchId)
                .orElse(null);
        if (batch.claimEpoch() > 0
                && !Objects.equals(
                        currentBatchId, batch.expectedPreviousBatchId())) {
            throw new PublicationConflictException(
                    "expected predecessor batch does not match the namespace head");
        }

        ProjectionSnapshot published = new ProjectionSnapshot(
                batch.id(),
                batch.namespace(),
                batch.generation(),
                batch.manifestFingerprint(),
                batch.requiredProjections(),
                publishedAt);
        heads.put(batch.namespace(), published);
        publicationHistory.put(
                new ProjectionGeneration(batch.namespace(), batch.generation()),
                published);
        publishedBatches.put(batch.id(), published);
        idempotentPublications.put(idempotencyKey, published);
        return published;
    }

    @Override
    public synchronized void abort(
            ProjectionBatch batch,
            String reason,
            Instant abortedAt) {
        Objects.requireNonNull(batch, "batch");
        requireText(reason, "reason");
        Objects.requireNonNull(abortedAt, "abortedAt");
        if (publishedBatches.containsKey(batch.id())) {
            throw new PublicationConflictException("a published batch cannot be aborted");
        }
        register(batch);
        abortedBatches.add(batch.id());
        registeredIdempotencyKeys.remove(
                new IdempotencyKey(batch.namespace(), batch.idempotencyKey()),
                batch.id());
    }

    @Override
    public synchronized ProjectionAbortOutcome abortIfUnreachable(
            ProjectionBatch batch,
            String reason,
            Instant abortedAt) {
        Objects.requireNonNull(batch, "batch");
        requireText(reason, "reason");
        Objects.requireNonNull(abortedAt, "abortedAt");
        ProjectionSnapshot published = publishedBatches.get(batch.id());
        if (published != null) {
            return ProjectionAbortOutcome.published(published);
        }
        if (commitPermits.containsKey(batch.id())) {
            return ProjectionAbortOutcome.keepStaging();
        }
        register(batch);
        abortedBatches.add(batch.id());
        registeredIdempotencyKeys.remove(
                new IdempotencyKey(batch.namespace(), batch.idempotencyKey()),
                batch.id());
        return ProjectionAbortOutcome.discardAllowed(
                new ProjectionDiscardPermit(UUID.randomUUID(), batch.id(), abortedAt));
    }

    private void register(ProjectionBatch batch) {
        ProjectionBatch registered = registeredBatches.get(batch.id());
        if (registered != null) {
            requireSameBatchIdentity(batch, registered);
            return;
        }

        IdempotencyKey idempotencyKey =
                new IdempotencyKey(batch.namespace(), batch.idempotencyKey());
        UUID registeredBatchId = registeredIdempotencyKeys.get(idempotencyKey);
        if (registeredBatchId != null && !registeredBatchId.equals(batch.id())) {
            throw new PublicationConflictException(
                    "an unpublished idempotency key cannot identify multiple batches");
        }
        registeredBatches.put(batch.id(), batch);
        registeredIdempotencyKeys.put(idempotencyKey, batch.id());
    }

    private static void requireSameBatchIdentity(
            ProjectionBatch candidate,
            ProjectionBatch registered) {
        if (!registered.namespace().equals(candidate.namespace())
                || registered.expectedPreviousGeneration()
                        != candidate.expectedPreviousGeneration()
                || !Objects.equals(
                        registered.expectedPreviousBatchId(),
                        candidate.expectedPreviousBatchId())
                || registered.generation() != candidate.generation()
                || !registered.idempotencyKey().equals(candidate.idempotencyKey())
                || !registered.manifestFingerprint().equals(candidate.manifestFingerprint())
                || !registered.requiredProjections().equals(candidate.requiredProjections())
                || registered.claimEpoch() != candidate.claimEpoch()) {
            throw new PublicationConflictException(
                    "a batch id cannot identify different publication content");
        }
    }

    private static void requireSamePublication(
            ProjectionBatch batch,
            ProjectionSnapshot existing) {
        if (!existing.namespace().equals(batch.namespace())
                || existing.generation() != batch.generation()
                || !existing.manifestFingerprint().equals(batch.manifestFingerprint())
                || !existing.projections().equals(batch.requiredProjections())) {
            throw new PublicationConflictException(
                    "an idempotency key cannot identify different publication content");
        }
    }

    private static void requireSameOperation(
            ProjectionBatch candidate, ProjectionBatch registered) {
        if (!candidate.namespace().equals(registered.namespace())
                || !candidate.idempotencyKey().equals(registered.idempotencyKey())
                || !candidate.manifestFingerprint().equals(registered.manifestFingerprint())
                || !candidate.requiredProjections().equals(registered.requiredProjections())) {
            throw new PublicationConflictException(
                    "an idempotency key cannot identify different publication content");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private record IdempotencyKey(ProjectionNamespace namespace, String value) {

        private IdempotencyKey {
            Objects.requireNonNull(namespace, "namespace");
            requireText(value, "value");
        }
    }

    private record ProjectionGeneration(
            ProjectionNamespace namespace,
            long generation) {

        private ProjectionGeneration {
            Objects.requireNonNull(namespace, "namespace");
            if (generation <= 0) {
                throw new IllegalArgumentException("generation must be positive");
            }
        }
    }
}
