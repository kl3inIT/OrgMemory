package com.orgmemory.graphrag.postgres;

import static com.orgmemory.graphrag.postgres.PostgresProjectionSupport.namespaceParameters;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL exact-cache adapter with namespace isolation and normalized evidence. */
public final class PostgresGraphRagCacheStore
        implements ModelInvocationCache, RetrievalResultCache {

    private static final String INSERT_EVIDENCE = """
            INSERT INTO graph_retrieval_cache_evidence (
                cache_entry_id,
                ordinal,
                organization_id,
                knowledge_asset_id,
                source_revision_id,
                chunk_id,
                acl_snapshot_id,
                acl_generation
            )
            VALUES (
                :cacheEntryId,
                :ordinal,
                :organizationId,
                :knowledgeAssetId,
                :sourceRevisionId,
                :chunkId,
                :aclSnapshotId,
                :aclGeneration
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final int batchSize;

    public PostgresGraphRagCacheStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this(jdbc, transactionManager, PostgresBatchOperations.DEFAULT_BATCH_SIZE);
    }

    public PostgresGraphRagCacheStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            int batchSize) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.batchSize = PostgresBatchOperations.requireBatchSize(batchSize);
    }

    @Override
    public Optional<ModelInvocationCache.Entry> get(
            ModelInvocationCache.Key key, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(now, "now");
        List<ModelInvocationCache.Entry> entries = jdbc.query("""
                SELECT media_type, payload, created_at, expires_at
                FROM graph_model_invocation_cache
                WHERE organization_id = :organizationId
                  AND workspace = :workspace
                  AND collection_name = :collection
                  AND operation = :operation
                  AND input_hash = :inputHash
                  AND model_route_fingerprint = :modelRouteFingerprint
                  AND profile_fingerprint = :profileFingerprint
                  AND expires_at > :now
                """,
                modelKeyParameters(key).addValue("now", Timestamp.from(now)),
                (resultSet, rowNumber) -> new ModelInvocationCache.Entry(
                        resultSet.getString("media_type"),
                        resultSet.getString("payload"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("expires_at").toInstant()));
        return entries.stream().findFirst();
    }

    @Override
    public void put(
            ModelInvocationCache.Key key, ModelInvocationCache.Entry entry) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entry, "entry");
        MapSqlParameterSource parameters = modelKeyParameters(key)
                .addValue("mediaType", entry.mediaType())
                .addValue("payload", entry.payload())
                .addValue("createdAt", Timestamp.from(entry.createdAt()))
                .addValue("expiresAt", Timestamp.from(entry.expiresAt()));
        jdbc.update("""
                INSERT INTO graph_model_invocation_cache (
                    organization_id,
                    workspace,
                    collection_name,
                    operation,
                    input_hash,
                    model_route_fingerprint,
                    profile_fingerprint,
                    media_type,
                    payload,
                    created_at,
                    expires_at
                )
                VALUES (
                    :organizationId,
                    :workspace,
                    :collection,
                    :operation,
                    :inputHash,
                    :modelRouteFingerprint,
                    :profileFingerprint,
                    :mediaType,
                    :payload,
                    :createdAt,
                    :expiresAt
                )
                ON CONFLICT (
                    organization_id,
                    workspace,
                    collection_name,
                    operation,
                    input_hash,
                    model_route_fingerprint,
                    profile_fingerprint
                )
                DO UPDATE SET
                    media_type = excluded.media_type,
                    payload = excluded.payload,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at
                """, parameters);
    }

    @Override
    public void putBounded(
            ProjectionNamespace namespace,
            String operation,
            Map<ModelInvocationCache.Key, ModelInvocationCache.Entry> entries,
            Instant now,
            int maximumEntries) {
        Objects.requireNonNull(namespace, "namespace");
        String boundedOperation = Objects.requireNonNull(operation, "operation").strip();
        Map<ModelInvocationCache.Key, ModelInvocationCache.Entry> boundedEntries =
                Map.copyOf(Objects.requireNonNull(entries, "entries"));
        Objects.requireNonNull(now, "now");
        if (boundedOperation.isEmpty()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        boundedEntries.forEach((key, entry) -> {
            if (!key.namespace().equals(namespace)
                    || !key.operation().equals(boundedOperation)) {
                throw new IllegalArgumentException(
                        "bounded entries must match namespace and operation");
            }
            Objects.requireNonNull(entry, "entry");
        });
        transactions.executeWithoutResult(status -> {
            acquireBoundedWriteLock(namespace, boundedOperation);
            boundedEntries.forEach(this::put);
            prune(namespace, boundedOperation, now, maximumEntries);
        });
    }

    private void acquireBoundedWriteLock(
            ProjectionNamespace namespace, String operation) {
        String lockKey = lockSegment(namespace.organizationId().toString())
                + lockSegment(namespace.workspace())
                + lockSegment(namespace.collection())
                + lockSegment(operation);
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
                new MapSqlParameterSource("lockKey", lockKey),
                resultSet -> {
                    resultSet.next();
                    return null;
                });
    }

    private static String lockSegment(String value) {
        return value.length() + ":" + value;
    }

    @Override
    public void invalidate(ProjectionNamespace namespace) {
        MapSqlParameterSource parameters = namespaceParameters(namespace);
        jdbc.update("""
                DELETE FROM graph_model_invocation_cache
                WHERE organization_id = :organizationId
                  AND workspace = :workspace
                  AND collection_name = :collection
                """, parameters);
    }

    @Override
    public void prune(
            ProjectionNamespace namespace,
            String operation,
            Instant now,
            int maximumEntries) {
        Objects.requireNonNull(namespace, "namespace");
        String boundedOperation = Objects.requireNonNull(operation, "operation").strip();
        Objects.requireNonNull(now, "now");
        if (boundedOperation.isEmpty()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        MapSqlParameterSource parameters = namespaceParameters(namespace)
                .addValue("operation", boundedOperation)
                .addValue("now", Timestamp.from(now))
                .addValue("maximumEntries", maximumEntries);
        jdbc.update("""
                DELETE FROM graph_model_invocation_cache target
                WHERE target.organization_id = :organizationId
                  AND target.workspace = :workspace
                  AND target.collection_name = :collection
                  AND target.operation = :operation
                  AND (
                    target.expires_at <= :now
                    OR (
                      target.input_hash,
                      target.model_route_fingerprint,
                      target.profile_fingerprint
                    ) IN (
                      SELECT
                        retained.input_hash,
                        retained.model_route_fingerprint,
                        retained.profile_fingerprint
                      FROM graph_model_invocation_cache retained
                      WHERE retained.organization_id = :organizationId
                        AND retained.workspace = :workspace
                        AND retained.collection_name = :collection
                        AND retained.operation = :operation
                        AND retained.expires_at > :now
                      ORDER BY retained.created_at DESC,
                        retained.input_hash DESC,
                        retained.model_route_fingerprint DESC,
                        retained.profile_fingerprint DESC
                      OFFSET :maximumEntries
                    )
                  )
                """, parameters);
    }

    @Override
    public int deleteExpired(String operation, Instant now, int maximumRows) {
        String boundedOperation = Objects.requireNonNull(operation, "operation").strip();
        Objects.requireNonNull(now, "now");
        if (boundedOperation.isEmpty()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (maximumRows <= 0) {
            throw new IllegalArgumentException("maximumRows must be positive");
        }
        return jdbc.update("""
                WITH expired AS (
                    SELECT ctid
                    FROM graph_model_invocation_cache
                    WHERE operation = :operation
                      AND expires_at <= :now
                    ORDER BY expires_at
                    LIMIT :maximumRows
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM graph_model_invocation_cache target
                USING expired
                WHERE target.ctid = expired.ctid
                """, new MapSqlParameterSource()
                .addValue("operation", boundedOperation)
                .addValue("now", Timestamp.from(now))
                .addValue("maximumRows", maximumRows));
    }

    @Override
    public Optional<RetrievalResultCache.Entry> get(
            RetrievalResultCache.Key key, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(now, "now");
        List<CachedRetrieval> rows = jdbc.query("""
                SELECT id, media_type, payload, created_at, expires_at
                FROM graph_retrieval_result_cache
                WHERE organization_id = :organizationId
                  AND workspace = :workspace
                  AND collection_name = :collection
                  AND publication_batch_id = :publicationBatchId
                  AND publication_generation = :publicationGeneration
                  AND publication_manifest_fingerprint =
                        :publicationManifestFingerprint
                  AND publication_kinds = :publicationKinds
                  AND authorization_fingerprint = :authorizationFingerprint
                  AND query_hash = :queryHash
                  AND strategy = :strategy
                  AND model_route_fingerprint = :modelRouteFingerprint
                  AND expires_at > :now
                """,
                retrievalKeyParameters(key).addValue("now", Timestamp.from(now)),
                (resultSet, rowNumber) -> new CachedRetrieval(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("media_type"),
                        resultSet.getString("payload"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("expires_at").toInstant()));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        CachedRetrieval row = rows.getFirst();
        List<EvidenceReference> evidence = jdbc.query("""
                SELECT organization_id,
                       knowledge_asset_id,
                       source_revision_id,
                       chunk_id,
                       acl_snapshot_id,
                       acl_generation
                FROM graph_retrieval_cache_evidence
                WHERE cache_entry_id = :cacheEntryId
                ORDER BY ordinal
                """,
                new MapSqlParameterSource("cacheEntryId", row.id()),
                (resultSet, rowNumber) -> new EvidenceReference(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("knowledge_asset_id", UUID.class),
                        resultSet.getObject("source_revision_id", UUID.class),
                        resultSet.getObject("chunk_id", UUID.class),
                        resultSet.getObject("acl_snapshot_id", UUID.class),
                        resultSet.getLong("acl_generation")));
        return Optional.of(new RetrievalResultCache.Entry(
                row.mediaType(),
                row.payload(),
                evidence,
                row.createdAt(),
                row.expiresAt()));
    }

    @Override
    public void put(
            RetrievalResultCache.Key key, RetrievalResultCache.Entry entry) {
        RetrievalResultCache.requireValidEntry(key, entry);
        transactions.executeWithoutResult(status -> {
            UUID entryId = upsertRetrieval(key, entry);
            MapSqlParameterSource delete =
                    new MapSqlParameterSource("cacheEntryId", entryId);
            jdbc.update(
                    "DELETE FROM graph_retrieval_cache_evidence "
                            + "WHERE cache_entry_id = :cacheEntryId",
                    delete);
            PostgresBatchOperations.batchUpdate(
                    jdbc,
                    INSERT_EVIDENCE,
                    entry.evidence(),
                    batchSize,
                    (ordinal, evidence) -> new MapSqlParameterSource()
                            .addValue("cacheEntryId", entryId)
                            .addValue("ordinal", ordinal)
                            .addValue("organizationId", evidence.organizationId())
                            .addValue("knowledgeAssetId", evidence.knowledgeAssetId())
                            .addValue("sourceRevisionId", evidence.sourceRevisionId())
                            .addValue("chunkId", evidence.chunkId())
                            .addValue("aclSnapshotId", evidence.aclSnapshotId())
                            .addValue("aclGeneration", evidence.aclGeneration()));
        });
    }

    @Override
    public void invalidateNamespace(ProjectionNamespace namespace) {
        MapSqlParameterSource parameters = namespaceParameters(namespace);
        jdbc.update("""
                DELETE FROM graph_retrieval_result_cache
                WHERE organization_id = :organizationId
                  AND workspace = :workspace
                  AND collection_name = :collection
                """, parameters);
    }

    private UUID upsertRetrieval(
            RetrievalResultCache.Key key, RetrievalResultCache.Entry entry) {
        MapSqlParameterSource parameters = retrievalKeyParameters(key)
                .addValue("id", UUID.randomUUID())
                .addValue("mediaType", entry.mediaType())
                .addValue("payload", entry.payload())
                .addValue("createdAt", Timestamp.from(entry.createdAt()))
                .addValue("expiresAt", Timestamp.from(entry.expiresAt()));
        return jdbc.queryForObject("""
                INSERT INTO graph_retrieval_result_cache (
                    id,
                    organization_id,
                    workspace,
                    collection_name,
                    publication_batch_id,
                    publication_generation,
                    publication_manifest_fingerprint,
                    publication_kinds,
                    authorization_fingerprint,
                    query_hash,
                    strategy,
                    model_route_fingerprint,
                    media_type,
                    payload,
                    created_at,
                    expires_at
                )
                VALUES (
                    :id,
                    :organizationId,
                    :workspace,
                    :collection,
                    :publicationBatchId,
                    :publicationGeneration,
                    :publicationManifestFingerprint,
                    :publicationKinds,
                    :authorizationFingerprint,
                    :queryHash,
                    :strategy,
                    :modelRouteFingerprint,
                    :mediaType,
                    :payload,
                    :createdAt,
                    :expiresAt
                )
                ON CONFLICT (
                    organization_id,
                    workspace,
                    collection_name,
                    publication_batch_id,
                    publication_generation,
                    publication_manifest_fingerprint,
                    publication_kinds,
                    authorization_fingerprint,
                    query_hash,
                    strategy,
                    model_route_fingerprint
                )
                DO UPDATE SET
                    media_type = excluded.media_type,
                    payload = excluded.payload,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at
                RETURNING id
                """, parameters, UUID.class);
    }

    private static MapSqlParameterSource modelKeyParameters(
            ModelInvocationCache.Key key) {
        return namespaceParameters(key.namespace())
                .addValue("operation", key.operation())
                .addValue("inputHash", key.inputHash())
                .addValue(
                        "modelRouteFingerprint",
                        key.modelRouteFingerprint())
                .addValue("profileFingerprint", key.profileFingerprint());
    }

    private static MapSqlParameterSource retrievalKeyParameters(
            RetrievalResultCache.Key key) {
        ProjectionSnapshot snapshot = key.snapshot();
        return namespaceParameters(snapshot.namespace())
                .addValue("publicationBatchId", snapshot.batchId())
                .addValue("publicationGeneration", snapshot.generation())
                .addValue(
                        "publicationManifestFingerprint",
                        snapshot.manifestFingerprint())
                .addValue(
                        "publicationKinds",
                        projectionKinds(snapshot.projections()))
                .addValue(
                        "authorizationFingerprint",
                        key.authorizationFingerprint())
                .addValue("queryHash", key.queryHash())
                .addValue("strategy", key.strategy())
                .addValue(
                        "modelRouteFingerprint",
                        key.modelRouteFingerprint());
    }

    private static String projectionKinds(Set<ProjectionKind> projections) {
        return projections.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private record CachedRetrieval(
            UUID id,
            String mediaType,
            String payload,
            Instant createdAt,
            Instant expiresAt) {
    }
}
