package com.orgmemory.graphrag.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectionBatchLifecycleTests {

    private static final Instant NOW = Instant.parse("2026-08-01T09:00:00Z");

    @Test
    void preparationThatFailsAfterPartialWriteIsAlsoDiscarded() {
        List<String> events = new ArrayList<>();
        ProjectionPublicationStore publications = new RecordingPublications(events);
        ProjectionBatch batch = new ProjectionBatch(
                id("failing-batch"),
                new ProjectionNamespace(id("organization"), "default", "knowledge"),
                0,
                1,
                "failing-batch",
                "manifest",
                Set.of(ProjectionKind.CONTENT),
                NOW);
        ProjectionBatchLifecycle.Preparation failing = new ProjectionBatchLifecycle.Preparation() {
            @Override
            public void prepare(ProjectionBatch ignored) {
                events.add("partial-write");
                throw new IllegalStateException("prepare failed");
            }

            @Override
            public ProjectionKind projectionKind() {
                return ProjectionKind.CONTENT;
            }

            @Override
            public void discard(
                    ProjectionBatch ignored, ProjectionDiscardPermit permit) {
                events.add("discard-current");
            }
        };

        assertThrows(
                IllegalStateException.class,
                () -> new ProjectionBatchLifecycle(publications)
                        .publish(
                                batch,
                                List.of(failing),
                                candidate -> new ProjectionCommitPermit(
                                        id("permit"),
                                        candidate.id(),
                                        candidate.manifestFingerprint(),
                                        1,
                                        NOW),
                                NOW));

        assertEquals(
                List.of("partial-write", "abort", "discard-current"),
                events);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record RecordingPublications(List<String> events)
            implements ProjectionPublicationStore {

        @Override
        public ProjectionBatch begin(ProjectionBatch candidate) {
            return candidate;
        }

        @Override
        public boolean hasBoundCommitPermit(ProjectionBatch batch) {
            return false;
        }

        @Override
        public Optional<ProjectionSnapshot> current(ProjectionNamespace namespace) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectionSnapshot> published(
                ProjectionNamespace namespace,
                long generation) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectionSnapshot> published(
                ProjectionNamespace namespace,
                String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void markPrepared(
                ProjectionBatch batch,
                ProjectionKind projection,
                Instant preparedAt) {
            throw new AssertionError("failed preparation must not be marked prepared");
        }

        @Override
        public ProjectionSnapshot publish(
                ProjectionBatch batch,
                ProjectionCommitPermit permit,
                Instant publishedAt) {
            throw new AssertionError("failed preparation must not publish");
        }

        @Override
        public void abort(
                ProjectionBatch batch,
                String reason,
                Instant abortedAt) {
            events.add("abort");
        }

        @Override
        public ProjectionAbortOutcome abortIfUnreachable(
                ProjectionBatch batch,
                String reason,
                Instant abortedAt) {
            events.add("abort");
            return ProjectionAbortOutcome.discardAllowed(
                    new ProjectionDiscardPermit(
                            id("discard"), batch.id(), abortedAt));
        }
    }
}
