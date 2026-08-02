package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
            Function<ProjectionBatch, ProjectionCommitPermit> permitIssuer,
            Instant now) {
        return publish(batch, preparations, permitIssuer, Optional.empty(), now);
    }

    public ProjectionSnapshot publish(
            ProjectionBatch batch,
            List<? extends Preparation> preparations,
            Function<ProjectionBatch, ProjectionCommitPermit> permitIssuer,
            Optional<ProjectionCommitPermit> recoveryPermit,
            Instant now) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(preparations, "preparations");
        Objects.requireNonNull(permitIssuer, "permitIssuer");
        Objects.requireNonNull(recoveryPermit, "recoveryPermit");
        Objects.requireNonNull(now, "now");
        ProjectionBatch registered = publications.begin(batch);
        Map<ProjectionKind, Preparation> byKind = validate(registered, preparations);
        List<Preparation> prepared = new ArrayList<>();
        boolean[] permitRequested = {recoveryPermit.isPresent()};
        boolean[] publishStarted = {false};
        try {
            if (recoveryPermit.isPresent()
                    || publications.hasBoundCommitPermit(registered)) {
                ProjectionCommitPermit storedPermit = recoveryPermit.orElseGet(() -> {
                    permitRequested[0] = true;
                    return Objects.requireNonNull(
                            permitIssuer.apply(registered), "permitIssuer result");
                });
                storedPermit.requireAuthorizes(registered);
                publishStarted[0] = true;
                return publications.publish(registered, storedPermit, now);
            }
            for (ProjectionKind kind : registered.requiredProjections().stream()
                    .sorted(Comparator.comparingInt(Enum::ordinal))
                    .toList()) {
                Preparation preparation = byKind.get(kind);
                // Register before prepare so a preparation that fails after a
                // partial write is itself discarded during saga cleanup.
                prepared.add(preparation);
                preparation.prepare(registered);
                publications.markPrepared(registered, kind, now);
            }
            permitRequested[0] = true;
            ProjectionCommitPermit permit = Objects.requireNonNull(
                    permitIssuer.apply(registered), "permitIssuer result");
            permit.requireAuthorizes(registered);
            publishStarted[0] = true;
            return publications.publish(registered, permit, now);
        } catch (RuntimeException failure) {
            if (publishStarted[0]) {
                ProjectionAbortOutcome outcome = reconcilePublishFailure(
                        registered, now, failure);
                if (outcome.status() == ProjectionAbortOutcome.Status.PUBLISHED) {
                    return outcome.publishedSnapshotOptional().orElseThrow();
                }
                if (outcome.status()
                        == ProjectionAbortOutcome.Status.DISCARD_ALLOWED) {
                    throw new PublicationRebaseRequiredException(
                            registered,
                            outcome.discardPermitOptional().orElseThrow(),
                            failure);
                }
            } else if (!permitRequested[0]) {
                abortAndDiscard(registered, prepared, now, failure);
            }
            throw failure;
        }
    }

    private ProjectionAbortOutcome reconcilePublishFailure(
            ProjectionBatch batch,
            Instant now,
            RuntimeException failure) {
        try {
            return publications.abortIfUnreachable(
                    batch, failure.getClass().getSimpleName(), now);
        } catch (RuntimeException reconciliationFailure) {
            failure.addSuppressed(reconciliationFailure);
            return ProjectionAbortOutcome.keepStaging();
        }
    }

    private void abortAndDiscard(
            ProjectionBatch batch,
            List<Preparation> prepared,
            Instant now,
            RuntimeException failure) {
        ProjectionAbortOutcome outcome;
        try {
            outcome = publications.abortIfUnreachable(
                    batch, failure.getClass().getSimpleName(), now);
        } catch (RuntimeException abortFailure) {
            failure.addSuppressed(abortFailure);
            return;
        }
        if (outcome.status() != ProjectionAbortOutcome.Status.DISCARD_ALLOWED) {
            return;
        }
        ProjectionDiscardPermit permit = outcome.discardPermitOptional().orElseThrow();
        for (int index = prepared.size() - 1; index >= 0; index--) {
            try {
                prepared.get(index).discard(batch, permit);
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
