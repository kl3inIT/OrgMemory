package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Atomic visibility head shared by every derived projection participating in
 * a publication batch.
 *
 * <p>Adapters stage generation data first. This store performs one
 * compare-and-set from the expected previous generation to the prepared
 * generation. Data at a generation that is not the current head is never
 * query-visible.
 *
 * <p>Required semantics:
 *
 * <ul>
 *   <li>publishing requires the prepared set to exactly match the batch plan;
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

    ProjectionSnapshot publish(
            ProjectionBatch batch,
            Set<ProjectionKind> preparedProjections,
            Instant publishedAt);

    void abort(ProjectionBatch batch, String reason, Instant abortedAt);

    final class PublicationConflictException extends RuntimeException {

        public PublicationConflictException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }
    }
}
