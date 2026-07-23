package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
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
    private final Map<UUID, ProjectionSnapshot> publishedBatches = new HashMap<>();
    private final Map<IdempotencyKey, ProjectionSnapshot> idempotentPublications =
            new HashMap<>();
    private final Set<UUID> abortedBatches = new HashSet<>();

    @Override
    public synchronized Optional<ProjectionSnapshot> current(ProjectionNamespace namespace) {
        return Optional.ofNullable(heads.get(Objects.requireNonNull(namespace, "namespace")));
    }

    @Override
    public synchronized ProjectionSnapshot publish(
            ProjectionBatch batch,
            Set<ProjectionKind> preparedProjections,
            Instant publishedAt) {
        Objects.requireNonNull(batch, "batch");
        Set<ProjectionKind> prepared =
                Set.copyOf(Objects.requireNonNull(preparedProjections, "preparedProjections"));
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (!prepared.equals(batch.requiredProjections())) {
            throw new IllegalArgumentException(
                    "preparedProjections must exactly match requiredProjections");
        }
        if (abortedBatches.contains(batch.id())) {
            throw new PublicationConflictException("an aborted batch cannot be published");
        }

        ProjectionSnapshot replay = publishedBatches.get(batch.id());
        if (replay != null) {
            requireSamePublication(batch, replay);
            return replay;
        }

        IdempotencyKey idempotencyKey =
                new IdempotencyKey(batch.namespace(), batch.idempotencyKey());
        ProjectionSnapshot idempotentReplay = idempotentPublications.get(idempotencyKey);
        if (idempotentReplay != null) {
            requireSamePublication(batch, idempotentReplay);
            publishedBatches.put(batch.id(), idempotentReplay);
            return idempotentReplay;
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
        abortedBatches.add(batch.id());
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
}
