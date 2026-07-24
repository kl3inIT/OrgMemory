package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Atomic visibility head shared by every derived projection participating in
 * a publication batch. Preparation receipts and the publication head live in
 * the same durable store. A receipt is recorded only after its projection
 * staging write commits; this is an ordered saga boundary, not a cross-store
 * transaction.
 *
 * <p>Adapters stage generation data first. This store performs one
 * compare-and-set from the expected previous generation to the prepared
 * generation. Data at an unpublished generation is never query-visible.
 *
 * <p>Required semantics:
 *
 * <ul>
 *   <li>publishing verifies a durable receipt for every required projection;
 *   <li>the head advances only when its current generation equals
 *       {@code expectedPreviousGeneration};
 *   <li>replaying the same batch or idempotency key with identical content
 *       returns the original snapshot when its manifest fingerprint matches;
 *   <li>reusing either identity for a different manifest fingerprint fails with
 *       {@link PublicationConflictException};
 *   <li>an aborted batch cannot later publish, and a published batch cannot be
 *       aborted.
 * </ul>
 */
public interface ProjectionPublicationStore {

    Optional<ProjectionSnapshot> current(ProjectionNamespace namespace);

    Optional<ProjectionSnapshot> published(
            ProjectionNamespace namespace,
            long generation);

    void markPrepared(
            ProjectionBatch batch,
            ProjectionKind projection,
            Instant preparedAt);

    ProjectionSnapshot publish(ProjectionBatch batch, Instant publishedAt);

    void abort(ProjectionBatch batch, String reason, Instant abortedAt);

    final class PublicationConflictException extends RuntimeException {

        public PublicationConflictException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }

        public PublicationConflictException(String message, Throwable cause) {
            super(
                    Objects.requireNonNull(message, "message"),
                    Objects.requireNonNull(cause, "cause"));
        }
    }

    final class PublicationNotReadyException extends RuntimeException {

        public PublicationNotReadyException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }
    }
}
