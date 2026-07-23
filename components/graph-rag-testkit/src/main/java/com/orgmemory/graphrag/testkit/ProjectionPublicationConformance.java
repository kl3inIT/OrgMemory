package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationNotReadyException;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reusable behavioral suite for every publication-head adapter.
 */
public final class ProjectionPublicationConformance {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "conformance", "knowledge");
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private ProjectionPublicationConformance() {
    }

    public static void verify(Supplier<? extends ProjectionPublicationStore> factory) {
        ProjectionPublicationStore store =
                Objects.requireNonNull(factory, "factory").get();
        Objects.requireNonNull(store, "factory result");
        ProjectionBatch first = batch(
                "first",
                0,
                1,
                "publication-1",
                Set.of(ProjectionKind.CONTENT, ProjectionKind.VECTOR));

        expect(PublicationNotReadyException.class, () -> store.publish(first, NOW));
        store.markPrepared(first, ProjectionKind.CONTENT, NOW);
        store.markPrepared(first, ProjectionKind.CONTENT, NOW.plusSeconds(1));
        expect(PublicationNotReadyException.class, () -> store.publish(first, NOW));
        expect(
                IllegalArgumentException.class,
                () -> store.markPrepared(first, ProjectionKind.LEXICAL, NOW));
        store.markPrepared(first, ProjectionKind.VECTOR, NOW);

        ProjectionSnapshot published = store.publish(first, NOW);
        require(published.generation() == 1, "first generation must publish");
        require(
                store.published(NAMESPACE, 1).orElseThrow().equals(published),
                "published history must contain the winning snapshot");
        require(
                store.publish(first, NOW.plusSeconds(1)).equals(published),
                "same-batch replay must return the original snapshot");

        ProjectionBatch idempotentReplay = batch(
                "first-retry",
                0,
                1,
                "publication-1",
                first.manifestFingerprint(),
                first.requiredProjections());
        require(
                store.publish(idempotentReplay, NOW.plusSeconds(2))
                        .equals(published),
                "same idempotency key must return the original snapshot");

        ProjectionBatch conflictingBatchReplay = batch(
                "first",
                0,
                1,
                "publication-1",
                "different-manifest",
                first.requiredProjections());
        expect(
                PublicationConflictException.class,
                () -> store.markPrepared(
                        conflictingBatchReplay,
                        ProjectionKind.CONTENT,
                        NOW));

        ProjectionBatch conflictingIdempotentReplay = batch(
                "first-conflict",
                0,
                1,
                "publication-1",
                "different-manifest",
                first.requiredProjections());
        expect(
                PublicationConflictException.class,
                () -> store.publish(conflictingIdempotentReplay, NOW));

        ProjectionBatch stale =
                batch("stale", 0, 1, "stale-publication", Set.of(ProjectionKind.CONTENT));
        store.markPrepared(stale, ProjectionKind.CONTENT, NOW);
        expect(
                PublicationConflictException.class,
                () -> store.publish(stale, NOW));

        ProjectionBatch aborted =
                batch("aborted", 1, 2, "aborted-publication", Set.of(ProjectionKind.CONTENT));
        store.abort(aborted, "conformance failure", NOW);
        expect(
                PublicationConflictException.class,
                () -> store.markPrepared(aborted, ProjectionKind.CONTENT, NOW));
        expect(
                PublicationConflictException.class,
                () -> store.publish(aborted, NOW));
        require(
                store.published(NAMESPACE, 2).isEmpty(),
                "aborted generations must not enter publication history");

        ProjectionBatch second = batch(
                "second",
                1,
                2,
                "publication-2",
                first.manifestFingerprint(),
                Set.of(ProjectionKind.CONTENT));
        store.markPrepared(second, ProjectionKind.CONTENT, NOW.plusSeconds(3));
        ProjectionSnapshot secondPublished = store.publish(second, NOW.plusSeconds(3));
        require(secondPublished.generation() == 2, "second generation must publish");
        require(
                secondPublished.manifestFingerprint().equals(first.manifestFingerprint()),
                "a manifest may be reused by a later publication");
        require(
                published.manifestFingerprint().equals(first.manifestFingerprint()),
                "published snapshots must expose the winning manifest fingerprint");
        require(
                store.published(NAMESPACE, 1).orElseThrow().equals(published),
                "old published snapshots must remain addressable after head advance");
    }

    private static ProjectionBatch batch(
            String id,
            long previous,
            long generation,
            String idempotencyKey,
            Set<ProjectionKind> projections) {
        return batch(
                id,
                previous,
                generation,
                idempotencyKey,
                "manifest-" + id,
                projections);
    }

    private static ProjectionBatch batch(
            String id,
            long previous,
            long generation,
            String idempotencyKey,
            String manifestFingerprint,
            Set<ProjectionKind> projections) {
        return new ProjectionBatch(
                UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)),
                NAMESPACE,
                previous,
                generation,
                idempotencyKey,
                manifestFingerprint,
                projections,
                NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(
            Class<? extends RuntimeException> expected,
            Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException failure) {
            if (expected.isInstance(failure)) {
                return;
            }
            throw new AssertionError(
                    "expected " + expected.getSimpleName() + " but got " + failure, failure);
        }
        throw new AssertionError("expected " + expected.getSimpleName());
    }
}
