package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL publication authority for tear-free, multi-projection snapshots.
 *
 * <p>Competing batches may stage the same next generation. Publication is
 * serialized per namespace and only the winning batch enters immutable
 * publication history. Projection readers address the winning batch id, never
 * generation-shaped staged rows.
 */
public final class PostgresProjectionPublicationStore
        implements ProjectionPublicationStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresProjectionPublicationStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public Optional<ProjectionSnapshot> current(ProjectionNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return querySnapshots(
                        """
                        SELECT publication.*
                        FROM projection_namespace_heads head
                        JOIN projection_publications publication
                          ON publication.batch_id = head.batch_id
                        WHERE head.organization_id = :organizationId
                          AND head.workspace = :workspace
                          AND head.collection_name = :collection
                        """,
                        namespaceParameters(namespace))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProjectionSnapshot> published(
            ProjectionNamespace namespace,
            long generation) {
        Objects.requireNonNull(namespace, "namespace");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        MapSqlParameterSource parameters =
                namespaceParameters(namespace).addValue("generation", generation);
        return querySnapshots(
                        """
                        SELECT *
                        FROM projection_publications
                        WHERE organization_id = :organizationId
                          AND workspace = :workspace
                          AND collection_name = :collection
                          AND generation = :generation
                        """,
                        parameters)
                .stream()
                .findFirst();
    }

    @Override
    public void markPrepared(
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
        transactions.executeWithoutResult(ignored -> {
            RegisteredBatch registered = ensureRegistered(batch);
            if ("PUBLISHED".equals(registered.status())) {
                return;
            }
            if (!"PREPARING".equals(registered.status())) {
                throw new PublicationConflictException(
                        "only a preparing batch can receive preparation receipts");
            }
            jdbc.update(
                    """
                    INSERT INTO projection_batch_receipts (
                        batch_id, projection_kind, prepared_at)
                    VALUES (:batchId, :projection, :preparedAt)
                    ON CONFLICT (batch_id, projection_kind) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("batchId", batch.id())
                            .addValue("projection", projection.name())
                            .addValue("preparedAt", Timestamp.from(preparedAt)));
        });
    }

    @Override
    public ProjectionSnapshot publish(
            ProjectionBatch batch,
            Instant publishedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(publishedAt, "publishedAt");
        return Objects.requireNonNull(transactions.execute(ignored -> publishInTransaction(
                batch, publishedAt)));
    }

    @Override
    public void abort(
            ProjectionBatch batch,
            String reason,
            Instant abortedAt) {
        Objects.requireNonNull(batch, "batch");
        String normalizedReason = requireText(reason, "reason");
        Objects.requireNonNull(abortedAt, "abortedAt");
        transactions.executeWithoutResult(ignored -> {
            RegisteredBatch registered = ensureRegistered(batch);
            if ("PUBLISHED".equals(registered.status())) {
                throw new PublicationConflictException(
                        "a published batch cannot be aborted");
            }
            if ("ABORTED".equals(registered.status())) {
                return;
            }
            jdbc.update(
                    """
                    UPDATE projection_batches
                    SET status = 'ABORTED',
                        aborted_at = :abortedAt,
                        abort_reason = :reason
                    WHERE batch_id = :batchId
                      AND status = 'PREPARING'
                    """,
                    new MapSqlParameterSource()
                            .addValue("batchId", batch.id())
                            .addValue("abortedAt", Timestamp.from(abortedAt))
                            .addValue("reason", normalizedReason));
        });
    }

    void ensureBatch(ProjectionBatch batch) {
        transactions.executeWithoutResult(ignored -> ensureRegistered(batch));
    }

    Optional<UUID> publishedBatchId(
            ProjectionNamespace namespace,
            long generation) {
        return published(namespace, generation).map(ProjectionSnapshot::batchId);
    }

    void requireReadable(
            ProjectionSnapshot snapshot,
            ProjectionKind kind) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(kind, "kind");
        ProjectionSnapshot persisted = published(
                        snapshot.namespace(), snapshot.generation())
                .filter(candidate -> candidate.batchId().equals(snapshot.batchId()))
                .filter(candidate ->
                        candidate.manifestFingerprint()
                                .equals(snapshot.manifestFingerprint()))
                .orElseThrow(() -> new PublicationConflictException(
                        "snapshot does not identify a published batch"));
        if (!persisted.projections().equals(snapshot.projections())) {
            throw new PublicationConflictException(
                    "snapshot does not exactly match the published batch");
        }
        if (!persisted.projections().contains(kind)) {
            throw new PublicationConflictException(
                    "snapshot does not contain projection " + kind);
        }
    }

    private ProjectionSnapshot publishInTransaction(
            ProjectionBatch batch,
            Instant publishedAt) {
        Optional<ProjectionSnapshot> batchReplay = publicationByBatch(batch.id());
        if (batchReplay.isPresent()) {
            RegisteredBatch registered = registeredById(batch.id()).orElseThrow();
            requireSameBatch(batch, registered);
            requireSamePublication(batch, batchReplay.orElseThrow());
            return batchReplay.orElseThrow();
        }

        Optional<ProjectionSnapshot> idempotentReplay =
                publicationByIdempotency(batch.namespace(), batch.idempotencyKey());
        if (idempotentReplay.isPresent()) {
            requireSamePublication(batch, idempotentReplay.orElseThrow());
            return idempotentReplay.orElseThrow();
        }

        RegisteredBatch registered = ensureRegistered(batch);
        if ("ABORTED".equals(registered.status())) {
            throw new PublicationConflictException(
                    "an aborted batch cannot be published");
        }
        lockNamespace(batch.namespace());

        Set<ProjectionKind> prepared = EnumSet.noneOf(ProjectionKind.class);
        prepared.addAll(jdbc.query(
                """
                SELECT projection_kind
                FROM projection_batch_receipts
                WHERE batch_id = :batchId
                """,
                Map.of("batchId", batch.id()),
                (resultSet, rowNumber) ->
                        ProjectionKind.valueOf(resultSet.getString(1))));
        if (!prepared.equals(batch.requiredProjections())) {
            throw new PublicationNotReadyException(
                    "every required projection must have a durable preparation receipt");
        }

        long currentGeneration = current(batch.namespace())
                .map(ProjectionSnapshot::generation)
                .orElse(0L);
        if (currentGeneration != batch.expectedPreviousGeneration()) {
            throw new PublicationConflictException(
                    "expected generation "
                            + batch.expectedPreviousGeneration()
                            + " but current generation is "
                            + currentGeneration);
        }

        MapSqlParameterSource parameters = batchParameters(batch)
                .addValue("publishedAt", Timestamp.from(publishedAt))
                .addValue("projections", encodeKinds(batch.requiredProjections()));
        jdbc.update(
                """
                INSERT INTO projection_publications (
                    batch_id, organization_id, workspace, collection_name,
                    generation, manifest_fingerprint, projections, published_at)
                VALUES (
                    :batchId, :organizationId, :workspace, :collection,
                    :generation, :manifestFingerprint, :projections, :publishedAt)
                """,
                parameters);
        jdbc.update(
                """
                INSERT INTO projection_namespace_heads (
                    organization_id, workspace, collection_name, batch_id, generation)
                VALUES (
                    :organizationId, :workspace, :collection, :batchId, :generation)
                ON CONFLICT (organization_id, workspace, collection_name)
                DO UPDATE SET
                    batch_id = EXCLUDED.batch_id,
                    generation = EXCLUDED.generation
                """,
                parameters);
        jdbc.update(
                """
                UPDATE projection_batches
                SET status = 'PUBLISHED', published_at = :publishedAt
                WHERE batch_id = :batchId AND status = 'PREPARING'
                """,
                parameters);
        return new ProjectionSnapshot(
                batch.id(),
                batch.namespace(),
                batch.generation(),
                batch.manifestFingerprint(),
                batch.requiredProjections(),
                publishedAt);
    }

    private RegisteredBatch ensureRegistered(ProjectionBatch batch) {
        Optional<RegisteredBatch> existing = registeredById(batch.id());
        if (existing.isPresent()) {
            requireSameBatch(batch, existing.orElseThrow());
            return existing.orElseThrow();
        }
        try {
            jdbc.update(
                    """
                    INSERT INTO projection_batches (
                        batch_id, organization_id, workspace, collection_name,
                        expected_previous_generation, generation, idempotency_key,
                        manifest_fingerprint, required_projections, status, created_at)
                    VALUES (
                        :batchId, :organizationId, :workspace, :collection,
                        :expectedPreviousGeneration, :generation, :idempotencyKey,
                        :manifestFingerprint, :requiredProjections, 'PREPARING', :createdAt)
                    """,
                    batchParameters(batch));
        } catch (DataIntegrityViolationException conflict) {
            throw new PublicationConflictException(
                    "a batch or idempotency key identifies different publication content");
        }
        return registeredById(batch.id()).orElseThrow();
    }

    private Optional<RegisteredBatch> registeredById(UUID batchId) {
        return jdbc.query(
                        """
                        SELECT *
                        FROM projection_batches
                        WHERE batch_id = :batchId
                        """,
                        Map.of("batchId", batchId),
                        (resultSet, rowNumber) -> registeredBatch(resultSet))
                .stream()
                .findFirst();
    }

    private Optional<ProjectionSnapshot> publicationByBatch(UUID batchId) {
        return querySnapshots(
                        """
                        SELECT *
                        FROM projection_publications
                        WHERE batch_id = :batchId
                        """,
                        new MapSqlParameterSource("batchId", batchId))
                .stream()
                .findFirst();
    }

    private Optional<ProjectionSnapshot> publicationByIdempotency(
            ProjectionNamespace namespace,
            String idempotencyKey) {
        return querySnapshots(
                        """
                        SELECT publication.*
                        FROM projection_publications publication
                        JOIN projection_batches batch
                          ON batch.batch_id = publication.batch_id
                        WHERE batch.organization_id = :organizationId
                          AND batch.workspace = :workspace
                          AND batch.collection_name = :collection
                          AND batch.idempotency_key = :idempotencyKey
                        """,
                        namespaceParameters(namespace)
                                .addValue("idempotencyKey", idempotencyKey))
                .stream()
                .findFirst();
    }

    private java.util.List<ProjectionSnapshot> querySnapshots(
            String sql,
            MapSqlParameterSource parameters) {
        return jdbc.query(sql, parameters, (resultSet, rowNumber) -> snapshot(resultSet));
    }

    private void lockNamespace(ProjectionNamespace namespace) {
        jdbc.query(
                """
                SELECT pg_advisory_xact_lock(
                    hashtextextended(
                        :organizationId || ':' || :workspace || ':' || :collection,
                        0))
                """,
                namespaceParameters(namespace),
                (RowCallbackHandler) resultSet -> {});
    }

    private static ProjectionSnapshot snapshot(ResultSet resultSet)
            throws SQLException {
        ProjectionNamespace namespace = new ProjectionNamespace(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("workspace"),
                resultSet.getString("collection_name"));
        return new ProjectionSnapshot(
                resultSet.getObject("batch_id", UUID.class),
                namespace,
                resultSet.getLong("generation"),
                resultSet.getString("manifest_fingerprint"),
                decodeKinds(resultSet.getString("projections")),
                resultSet.getTimestamp("published_at").toInstant());
    }

    private static RegisteredBatch registeredBatch(ResultSet resultSet)
            throws SQLException {
        return new RegisteredBatch(
                resultSet.getObject("batch_id", UUID.class),
                new ProjectionNamespace(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getString("workspace"),
                        resultSet.getString("collection_name")),
                resultSet.getLong("expected_previous_generation"),
                resultSet.getLong("generation"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("manifest_fingerprint"),
                decodeKinds(resultSet.getString("required_projections")),
                resultSet.getString("status"));
    }

    private static void requireSameBatch(
            ProjectionBatch batch,
            RegisteredBatch registered) {
        if (!batch.id().equals(registered.id())
                || !batch.namespace().equals(registered.namespace())
                || batch.expectedPreviousGeneration()
                        != registered.expectedPreviousGeneration()
                || batch.generation() != registered.generation()
                || !batch.idempotencyKey().equals(registered.idempotencyKey())
                || !batch.manifestFingerprint().equals(registered.manifestFingerprint())
                || !batch.requiredProjections().equals(registered.requiredProjections())) {
            throw new PublicationConflictException(
                    "a batch id cannot identify different publication content");
        }
    }

    private static void requireSamePublication(
            ProjectionBatch batch,
            ProjectionSnapshot snapshot) {
        if (!batch.namespace().equals(snapshot.namespace())
                || batch.generation() != snapshot.generation()
                || !batch.manifestFingerprint().equals(snapshot.manifestFingerprint())
                || !batch.requiredProjections().equals(snapshot.projections())) {
            throw new PublicationConflictException(
                    "an idempotency key cannot identify different publication content");
        }
    }

    private static MapSqlParameterSource batchParameters(ProjectionBatch batch) {
        return namespaceParameters(batch.namespace())
                .addValue("batchId", batch.id())
                .addValue(
                        "expectedPreviousGeneration",
                        batch.expectedPreviousGeneration())
                .addValue("generation", batch.generation())
                .addValue("idempotencyKey", batch.idempotencyKey())
                .addValue("manifestFingerprint", batch.manifestFingerprint())
                .addValue(
                        "requiredProjections",
                        encodeKinds(batch.requiredProjections()))
                .addValue("createdAt", Timestamp.from(batch.createdAt()));
    }

    private static MapSqlParameterSource namespaceParameters(
            ProjectionNamespace namespace) {
        return new MapSqlParameterSource()
                .addValue("organizationId", namespace.organizationId())
                .addValue("workspace", namespace.workspace())
                .addValue("collection", namespace.collection());
    }

    private static String encodeKinds(Set<ProjectionKind> kinds) {
        return kinds.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private static Set<ProjectionKind> decodeKinds(String encoded) {
        EnumSet<ProjectionKind> result = EnumSet.noneOf(ProjectionKind.class);
        Arrays.stream(encoded.split(","))
                .filter(value -> !value.isBlank())
                .map(ProjectionKind::valueOf)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private record RegisteredBatch(
            UUID id,
            ProjectionNamespace namespace,
            long expectedPreviousGeneration,
            long generation,
            String idempotencyKey,
            String manifestFingerprint,
            Set<ProjectionKind> requiredProjections,
            String status) {}
}
