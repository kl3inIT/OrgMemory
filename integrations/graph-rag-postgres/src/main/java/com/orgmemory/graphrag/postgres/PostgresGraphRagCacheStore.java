package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresGraphRagCacheStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
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
            int ordinal = 0;
            for (EvidenceReference evidence : entry.evidence()) {
                jdbc.update("""
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
                        """,
                        new MapSqlParameterSource()
                                .addValue("cacheEntryId", entryId)
                                .addValue("ordinal", ordinal++)
                                .addValue(
                                        "organizationId",
                                        evidence.organizationId())
                                .addValue(
                                        "knowledgeAssetId",
                                        evidence.knowledgeAssetId())
                                .addValue(
                                        "sourceRevisionId",
                                        evidence.sourceRevisionId())
                                .addValue("chunkId", evidence.chunkId())
                                .addValue(
                                        "aclSnapshotId",
                                        evidence.aclSnapshotId())
                                .addValue(
                                        "aclGeneration",
                                        evidence.aclGeneration()));
            }
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

    public void invalidateAll(ProjectionNamespace namespace) {
        transactions.executeWithoutResult(status -> {
            invalidate(namespace);
            invalidateNamespace(namespace);
        });
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

    private static MapSqlParameterSource namespaceParameters(
            ProjectionNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return new MapSqlParameterSource()
                .addValue("organizationId", namespace.organizationId())
                .addValue("workspace", namespace.workspace())
                .addValue("collection", namespace.collection());
    }

    private static String projectionKinds(Set<ProjectionKind> projections) {
        return projections.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    static Set<ProjectionKind> parseProjectionKinds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(ProjectionKind::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record CachedRetrieval(
            UUID id,
            String mediaType,
            String payload,
            Instant createdAt,
            Instant expiresAt) {
    }
}
