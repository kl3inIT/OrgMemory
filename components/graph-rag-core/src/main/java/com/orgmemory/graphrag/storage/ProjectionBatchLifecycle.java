package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executes the prepare/publish/abort lifecycle shared by projection adapters.
 *
 * <p>Each preparation must commit its staging write before returning. The
 * publication receipt is then recorded. On failure the batch is durably
 * aborted and every prepared projection is asked to discard its unreachable
 * staged data.
 */
public final class ProjectionBatchLifecycle {

    private final ProjectionPublicationStore publications;

    public ProjectionBatchLifecycle(ProjectionPublicationStore publications) {
        this.publications = Objects.requireNonNull(publications, "publications");
    }

    public ProjectionSnapshot publish(
            ProjectionBatch batch,
            List<? extends Preparation> preparations,
            Instant now) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(preparations, "preparations");
        Objects.requireNonNull(now, "now");
        Map<ProjectionKind, Preparation> byKind = validate(batch, preparations);
        List<Preparation> prepared = new ArrayList<>();
        try {
            for (ProjectionKind kind : batch.requiredProjections().stream()
                    .sorted(Comparator.comparingInt(Enum::ordinal))
                    .toList()) {
                Preparation preparation = byKind.get(kind);
                preparation.prepare(batch);
                prepared.add(preparation);
                publications.markPrepared(batch, kind, now);
            }
            return publications.publish(batch, now);
        } catch (RuntimeException failure) {
            abortAndDiscard(batch, prepared, now, failure);
            throw failure;
        }
    }

    private void abortAndDiscard(
            ProjectionBatch batch,
            List<Preparation> prepared,
            Instant now,
            RuntimeException failure) {
        try {
            publications.abort(batch, failure.getClass().getSimpleName(), now);
        } catch (RuntimeException abortFailure) {
            failure.addSuppressed(abortFailure);
        }
        for (int index = prepared.size() - 1; index >= 0; index--) {
            try {
                prepared.get(index).discard(batch);
            } catch (RuntimeException discardFailure) {
                failure.addSuppressed(discardFailure);
            }
        }
    }

    private static Map<ProjectionKind, Preparation> validate(
            ProjectionBatch batch,
            List<? extends Preparation> preparations) {
        Map<ProjectionKind, Preparation> byKind =
                new EnumMap<>(ProjectionKind.class);
        for (Preparation preparation : preparations) {
            Objects.requireNonNull(preparation, "preparation");
            if (byKind.put(preparation.projectionKind(), preparation) != null) {
                throw new IllegalArgumentException(
                        "duplicate preparation for " + preparation.projectionKind());
            }
        }
        Set<ProjectionKind> actual = Set.copyOf(byKind.keySet());
        if (!actual.equals(batch.requiredProjections())) {
            throw new IllegalArgumentException(
                    "preparations must exactly match required projections");
        }
        return byKind;
    }

    public interface Preparation extends StagedProjectionWriter {

        void prepare(ProjectionBatch batch);
    }
}
