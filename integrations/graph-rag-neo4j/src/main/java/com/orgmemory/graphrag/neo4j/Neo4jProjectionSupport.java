package com.orgmemory.graphrag.neo4j;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

final class Neo4jProjectionSupport {

    private static final Duration COPY_POLL_INTERVAL = Duration.ofMillis(50);

    private static final List<String> COPY_NODE_QUERIES = List.of(
            """
            MATCH (source:OrgMemoryGraphEntity {batchId: $previousBatchId})
            WITH source ORDER BY source.key SKIP $offset LIMIT $pageSize
            WITH collect(source) AS page
            CALL {
                WITH page
                UNWIND page AS source
                MERGE (copy:OrgMemoryGraphEntity {
                    key: $batchId + ':' + source.entityId
                })
                SET copy = source {
                    .*,
                    key: $batchId + ':' + source.entityId,
                    batchId: $batchId,
                    generation: $generation
                }
                RETURN count(copy) AS copied
            }
            RETURN size(page) AS consumed, copied
            """,
            """
            MATCH (source:OrgMemoryGraphEntityContribution {
                batchId: $previousBatchId
            })
            WITH source ORDER BY source.key SKIP $offset LIMIT $pageSize
            WITH collect(source) AS page
            CALL {
                WITH page
                UNWIND page AS source
                MERGE (copy:OrgMemoryGraphEntityContribution {
                    key: $batchId + ':' + source.contributionId
                })
                SET copy = source {
                    .*,
                    key: $batchId + ':' + source.contributionId,
                    batchId: $batchId,
                    generation: $generation
                }
                WITH source, copy
                MATCH (entity:OrgMemoryGraphEntity {
                    key: $batchId + ':' + source.entityId
                })
                MERGE (copy)-[:CONTRIBUTES_TO]->(entity)
                RETURN count(copy) AS copied
            }
            RETURN size(page) AS consumed, copied
            """,
            """
            MATCH (source:OrgMemoryGraphRelationContribution {
                batchId: $previousBatchId
            })
            WITH source ORDER BY source.key SKIP $offset LIMIT $pageSize
            WITH collect(source) AS page
            CALL {
                WITH page
                UNWIND page AS source
                MERGE (copy:OrgMemoryGraphRelationContribution {
                    key: $batchId + ':' + source.contributionId
                })
                SET copy = source {
                    .*,
                    key: $batchId + ':' + source.contributionId,
                    batchId: $batchId,
                    generation: $generation
                }
                RETURN count(copy) AS copied
            }
            RETURN size(page) AS consumed, copied
            """);

    private static final String COPY_RELATIONS = """
            MATCH (source:OrgMemoryGraphEntity)-[relation:ORGMEMORY_RELATES {
                batchId: $previousBatchId
            }]->(target:OrgMemoryGraphEntity)
            WITH source, target, relation
            ORDER BY relation.key SKIP $offset LIMIT $pageSize
            WITH collect({
                sourceEntityId: source.entityId,
                targetEntityId: target.entityId,
                relation: relation
            }) AS page
            CALL {
                WITH page
                UNWIND page AS row
                WITH row, row.relation AS relation
                MATCH (copySource:OrgMemoryGraphEntity {
                    key: $batchId + ':' + row.sourceEntityId
                })
                MATCH (copyTarget:OrgMemoryGraphEntity {
                    key: $batchId + ':' + row.targetEntityId
                })
                MERGE (copySource)-[copy:ORGMEMORY_RELATES {
                    key: $batchId + ':' + relation.relationId
                }]->(copyTarget)
                SET copy = relation {
                    .*,
                    key: $batchId + ':' + relation.relationId,
                    batchId: $batchId,
                    generation: $generation
                }
                RETURN count(copy) AS copied
            }
            RETURN size(page) AS consumed, copied
            """;

    private final Neo4jOperations operations;
    private final ProjectionPublicationStore publications;
    private final int copyPageSize;
    private final Duration copyWaitTimeout;
    private final Duration copyLease;

    Neo4jProjectionSupport(
            Neo4jOperations operations,
            ProjectionPublicationStore publications,
            int copyPageSize,
            Duration copyWaitTimeout,
            Duration copyLease) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.publications = Objects.requireNonNull(publications, "publications");
        if (copyPageSize <= 0) {
            throw new IllegalArgumentException("copyPageSize must be positive");
        }
        this.copyPageSize = copyPageSize;
        this.copyWaitTimeout = requirePositive(copyWaitTimeout, "copyWaitTimeout");
        this.copyLease = requirePositive(copyLease, "copyLease");
    }

    void stage(ProjectionBatch batch, Runnable mutation) {
        requireBatch(batch);
        Objects.requireNonNull(mutation, "mutation");
        ensureCopyForward(batch);
        mutation.run();
    }

    void requireReadable(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and snapshot must share an organization");
        }
        ProjectionSnapshot persisted = publications
                .published(snapshot.namespace(), snapshot.generation())
                .filter(snapshot::equals)
                .orElseThrow(() -> new ProjectionPublicationStore.PublicationConflictException(
                        "snapshot does not identify the exact published batch"));
        if (!persisted.projections().contains(ProjectionKind.GRAPH)) {
            throw new ProjectionPublicationStore.PublicationConflictException(
                    "snapshot does not contain the graph projection");
        }
    }

    Map<String, Object> visibility(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        requireReadable(scope, snapshot);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("batchId", snapshot.batchId().toString());
        parameters.put("organizationId", scope.organizationId().toString());
        parameters.put("workspace", snapshot.namespace().workspace());
        parameters.put("collection", snapshot.namespace().collection());
        parameters.put("generation", snapshot.generation());
        parameters.put(
                "authorizedAssetIds",
                scope.authorizedAssetIds().stream()
                        .sorted()
                        .map(UUID::toString)
                        .toList());
        return Map.copyOf(parameters);
    }

    void discard(ProjectionBatch batch, int pageSize) {
        requireBatch(batch);
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        Map<String, Object> parameters =
                Map.of("batchId", batch.id().toString(), "pageSize", pageSize);
        deleteUntilEmpty(
                """
                MATCH ()-[relation:ORGMEMORY_RELATES {batchId: $batchId}]->()
                WITH relation LIMIT $pageSize
                DELETE relation
                RETURN count(relation) AS deleted
                """,
                parameters);
        for (String label : List.of(
                "OrgMemoryGraphRelationContribution",
                "OrgMemoryGraphEntityContribution",
                "OrgMemoryGraphEntity")) {
            deleteUntilEmpty(
                    """
                    MATCH (node:%s {batchId: $batchId})
                    WITH node LIMIT $pageSize
                    DETACH DELETE node
                    RETURN count(node) AS deleted
                    """
                            .formatted(label),
                    parameters);
        }
        operations.writeWithoutResult(transaction -> transaction.run(
                        """
                        MATCH (stage:OrgMemoryGraphStage {key: $key})
                        DELETE stage
                        """,
                        Map.of("key", stageKey(batch)))
                .consume());
    }

    private void ensureCopyForward(ProjectionBatch batch) {
        String owner = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plus(copyWaitTimeout);
        while (Instant.now().isBefore(deadline)) {
            StageState state = stageState(batch);
            if ("READY".equals(state.status())) {
                return;
            }
            long now = System.currentTimeMillis();
            long staleBefore = now - copyLease.toMillis();
            boolean acquired = operations.write(transaction -> transaction.run(
                            """
                            MATCH (stage:OrgMemoryGraphStage {key: $key})
                            WHERE stage.status IN ['NEW', 'FAILED']
                               OR (stage.status = 'COPYING'
                                   AND stage.leaseRenewedAt < $staleBefore)
                            SET stage.status = 'COPYING',
                                stage.owner = $owner,
                                stage.leaseRenewedAt = $now,
                                stage.error = null
                            RETURN count(stage) AS acquired
                            """,
                            Map.of(
                                    "key", stageKey(batch),
                                    "owner", owner,
                                    "now", now,
                                    "staleBefore", staleBefore))
                    .single()
                    .get("acquired")
                    .asLong() == 1L);
            if (acquired) {
                copyAsOwner(batch, owner);
                return;
            }
            park();
        }
        throw new Neo4jGraphProjectionException(
                "timed out waiting for graph copy-forward " + batch.id());
    }

    private StageState stageState(ProjectionBatch batch) {
        return operations.write(transaction -> {
            var record = transaction.run(
                            """
                            MERGE (stage:OrgMemoryGraphStage {key: $key})
                            ON CREATE SET
                                stage.batchId = $batchId,
                                stage.projectionKind = 'GRAPH',
                                stage.status = 'NEW',
                                stage.leaseRenewedAt = 0
                            RETURN stage.status AS status,
                                   stage.owner AS owner,
                                   stage.leaseRenewedAt AS leaseRenewedAt
                            """,
                            Map.of(
                                    "key", stageKey(batch),
                                    "batchId", batch.id().toString()))
                    .single();
            String owner =
                    record.get("owner").isNull() ? "" : record.get("owner").asString();
            return new StageState(
                    record.get("status").asString(),
                    owner,
                    record.get("leaseRenewedAt").asLong());
        });
    }

    private void copyAsOwner(ProjectionBatch batch, String owner) {
        try {
            if (batch.expectedPreviousGeneration() > 0) {
                ProjectionSnapshot previous = publications
                        .published(
                                batch.namespace(),
                                batch.expectedPreviousGeneration())
                        .orElseThrow(() -> new Neo4jGraphProjectionException(
                                "previous published graph snapshot is unavailable"));
                if (previous.projections().contains(ProjectionKind.GRAPH)) {
                    Map<String, Object> parameters = new LinkedHashMap<>();
                    parameters.put("previousBatchId", previous.batchId().toString());
                    parameters.put("batchId", batch.id().toString());
                    parameters.put("generation", batch.generation());
                    for (String query : COPY_NODE_QUERIES) {
                        copyPaged(query, parameters, batch, owner);
                    }
                    copyPaged(COPY_RELATIONS, parameters, batch, owner);
                }
            }
            long updated = operations.write(transaction -> transaction.run(
                            """
                            MATCH (stage:OrgMemoryGraphStage {
                                key: $key, owner: $owner, status: 'COPYING'
                            })
                            SET stage.status = 'READY',
                                stage.completedAt = $now
                            RETURN count(stage) AS updated
                            """,
                            Map.of(
                                    "key", stageKey(batch),
                                    "owner", owner,
                                    "now", System.currentTimeMillis()))
                    .single()
                    .get("updated")
                    .asLong());
            if (updated != 1L) {
                waitForReady(batch);
            }
        } catch (RuntimeException exception) {
            operations.writeWithoutResult(transaction -> transaction.run(
                            """
                            MATCH (stage:OrgMemoryGraphStage {
                                key: $key, owner: $owner, status: 'COPYING'
                            })
                            SET stage.status = 'FAILED',
                                stage.error = $error
                            """,
                            Map.of(
                                    "key", stageKey(batch),
                                    "owner", owner,
                                    "error", exception.getClass().getSimpleName()))
                    .consume());
            throw exception;
        }
    }

    private void copyPaged(
            String query,
            Map<String, Object> baseParameters,
            ProjectionBatch batch,
            String owner) {
        long offset = 0;
        while (true) {
            Map<String, Object> parameters = new LinkedHashMap<>(baseParameters);
            parameters.put("offset", offset);
            parameters.put("pageSize", copyPageSize);
            long consumed = operations.write(transaction -> transaction.run(query, parameters)
                    .single()
                    .get("consumed")
                    .asLong());
            renewLease(batch, owner);
            if (consumed < copyPageSize) {
                return;
            }
            offset += consumed;
        }
    }

    private void renewLease(ProjectionBatch batch, String owner) {
        long renewed = operations.write(transaction -> transaction.run(
                        """
                        MATCH (stage:OrgMemoryGraphStage {
                            key: $key, owner: $owner, status: 'COPYING'
                        })
                        SET stage.leaseRenewedAt = $now
                        RETURN count(stage) AS renewed
                        """,
                        Map.of(
                                "key", stageKey(batch),
                                "owner", owner,
                                "now", System.currentTimeMillis()))
                .single()
                .get("renewed")
                .asLong());
        if (renewed != 1L) {
            throw new Neo4jGraphProjectionException(
                    "graph copy-forward ownership changed for " + batch.id());
        }
    }

    private void waitForReady(ProjectionBatch batch) {
        Instant deadline = Instant.now().plus(copyWaitTimeout);
        while (Instant.now().isBefore(deadline)) {
            if ("READY".equals(stageState(batch).status())) {
                return;
            }
            park();
        }
        throw new Neo4jGraphProjectionException(
                "graph copy-forward did not reach READY for " + batch.id());
    }

    private void deleteUntilEmpty(String query, Map<String, Object> parameters) {
        while (true) {
            long deleted = operations.write(transaction -> transaction.run(query, parameters)
                    .single()
                    .get("deleted")
                    .asLong());
            if (deleted == 0L) {
                return;
            }
        }
    }

    private static void requireBatch(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (!batch.requiredProjections().contains(ProjectionKind.GRAPH)) {
            throw new IllegalArgumentException(
                    "batch does not require the graph projection");
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        Duration duration = Objects.requireNonNull(value, field);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return duration;
    }

    private static String stageKey(ProjectionBatch batch) {
        return batch.id() + ":GRAPH";
    }

    private static void park() {
        LockSupport.parkNanos(COPY_POLL_INTERVAL.toNanos());
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw new Neo4jGraphProjectionException(
                    "interrupted while waiting for graph copy-forward");
        }
    }

    private record StageState(String status, String owner, long leaseRenewedAt) {
    }
}
