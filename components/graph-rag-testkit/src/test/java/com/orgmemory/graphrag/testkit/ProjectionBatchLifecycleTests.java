package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionBatchLifecycle;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProjectionBatchLifecycleTests {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final ProjectionNamespace NAMESPACE = new ProjectionNamespace(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            "default",
            "knowledge");

    @Test
    void publishesOnlyAfterEveryPreparationCommits() {
        InMemoryProjectionPublicationStore store =
                new InMemoryProjectionPublicationStore();
        ProjectionBatch batch = batch("success");
        List<String> events = new ArrayList<>();
        ProjectionBatchLifecycle lifecycle = new ProjectionBatchLifecycle(store);

        var snapshot = lifecycle.publish(
                batch,
                List.of(
                        preparation(ProjectionKind.CONTENT, events, false),
                        preparation(ProjectionKind.VECTOR, events, false)),
                candidate -> ProjectionPermitFixtures.commitPermit(candidate, NOW),
                NOW);

        assertEquals(1, snapshot.generation());
        assertEquals(List.of("prepare-CONTENT", "prepare-VECTOR"), events);
    }

    @Test
    void failedPreparationAbortsAndDiscardsPreparedDataInReverseOrder() {
        InMemoryProjectionPublicationStore store =
                new InMemoryProjectionPublicationStore();
        ProjectionBatch batch = batch("failure");
        List<String> events = new ArrayList<>();
        ProjectionBatchLifecycle lifecycle = new ProjectionBatchLifecycle(store);

        assertThrows(
                IllegalStateException.class,
                () -> lifecycle.publish(
                        batch,
                        List.of(
                                preparation(
                                        ProjectionKind.CONTENT, events, false),
                                preparation(
                                        ProjectionKind.VECTOR, events, true)),
                        candidate -> ProjectionPermitFixtures.commitPermit(candidate, NOW),
                        NOW));

        assertEquals(
                List.of(
                        "prepare-CONTENT",
                        "prepare-VECTOR",
                        "discard-VECTOR",
                        "discard-CONTENT"),
                events);
        assertTrue(store.current(NAMESPACE).isEmpty());
        assertThrows(
                RuntimeException.class,
                () -> store.publish(
                        batch,
                        ProjectionPermitFixtures.commitPermit(batch, NOW.plusSeconds(1)),
                        NOW.plusSeconds(1)));
    }

    @Test
    void ambiguousPermitAcknowledgementKeepsStagingAndRecoveryDoesNotRestage() {
        InMemoryProjectionPublicationStore store =
                new InMemoryProjectionPublicationStore();
        ProjectionBatch batch = batch("permit-ambiguity");
        List<String> events = new ArrayList<>();
        AtomicReference<com.orgmemory.graphrag.storage.ProjectionCommitPermit> durablePermit =
                new AtomicReference<>();
        ProjectionBatchLifecycle lifecycle = new ProjectionBatchLifecycle(store);

        assertThrows(
                IllegalStateException.class,
                () -> lifecycle.publish(
                        batch,
                        List.of(
                                preparation(ProjectionKind.CONTENT, events, false),
                                preparation(ProjectionKind.VECTOR, events, false)),
                        candidate -> {
                            durablePermit.set(ProjectionPermitFixtures.commitPermit(
                                    candidate, NOW));
                            throw new IllegalStateException(
                                    "commit acknowledgement was lost");
                        },
                        NOW));

        assertEquals(List.of("prepare-CONTENT", "prepare-VECTOR"), events);
        var recovered = lifecycle.publish(
                batch,
                List.of(
                        preparationThatMustNotRun(ProjectionKind.CONTENT),
                        preparationThatMustNotRun(ProjectionKind.VECTOR)),
                candidate -> {
                    throw new AssertionError("the durable permit must be reused");
                },
                Optional.of(durablePermit.get()),
                NOW.plusSeconds(1));

        assertEquals(batch.id(), recovered.batchId());
    }

    private static ProjectionBatchLifecycle.Preparation preparation(
            ProjectionKind kind, List<String> events, boolean fail) {
        return new ProjectionBatchLifecycle.Preparation() {
            @Override
            public void prepare(ProjectionBatch batch) {
                events.add("prepare-" + kind);
                if (fail) {
                    throw new IllegalStateException("failed " + kind);
                }
            }

            @Override
            public ProjectionKind projectionKind() {
                return kind;
            }

            @Override
            public void discard(
                    ProjectionBatch batch,
                    com.orgmemory.graphrag.storage.ProjectionDiscardPermit permit) {
                events.add("discard-" + kind);
            }
        };
    }

    private static ProjectionBatchLifecycle.Preparation preparationThatMustNotRun(
            ProjectionKind kind) {
        return new ProjectionBatchLifecycle.Preparation() {
            @Override
            public void prepare(ProjectionBatch batch) {
                throw new AssertionError("recovery after a durable permit must not restage");
            }

            @Override
            public ProjectionKind projectionKind() {
                return kind;
            }

            @Override
            public void discard(
                    ProjectionBatch batch,
                    com.orgmemory.graphrag.storage.ProjectionDiscardPermit permit) {
                throw new AssertionError("recovery after a durable permit must not discard");
            }
        };
    }

    private static ProjectionBatch batch(String value) {
        return new ProjectionBatch(
                UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)),
                NAMESPACE,
                0,
                1,
                "publication-" + value,
                "manifest-" + value,
                Set.of(ProjectionKind.CONTENT, ProjectionKind.VECTOR),
                NOW);
    }
}
