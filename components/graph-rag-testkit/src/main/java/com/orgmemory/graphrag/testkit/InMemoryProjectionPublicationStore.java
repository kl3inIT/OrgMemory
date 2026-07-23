package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.storage.ProjectionBatch;
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
    private final Set<UUID> abortedBatches = new HashSet<>();

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
            Instant publishedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (abortedBatches.contains(batch.id())) {
            throw new PublicationConflictException("an aborted batch cannot be published");
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
                || registered.generation() != candidate.generation()
                || !registered.idempotencyKey().equals(candidate.idempotencyKey())
                || !registered.manifestFingerprint().equals(candidate.manifestFingerprint())
                || !registered.requiredProjections().equals(candidate.requiredProjections())) {
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
