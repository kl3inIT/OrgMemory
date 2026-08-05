package com.orgmemory.graphrag.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Test-scope prototype for ADR 0020's authorized multi-snapshot query plane.
 *
 * <p>This class is deliberately outside main source sets and Spring runtime wiring. It validates exact
 * publication tuples, ranks only authorized vectors, expands through only authorized depth-one
 * relations, and returns contribution-level identity for independent fail-closed validation.
 */
final class AuthorizedMultiSnapshotQuery {

    private static final String COMPOUND_SCOPES = """
            WITH requested_scopes AS (
                SELECT *
                FROM jsonb_to_recordset(CAST(:scopes AS jsonb)) AS requested(
                    space_id uuid,
                    batch_id uuid,
                    generation bigint,
                    manifest_fingerprint text,
                    acl_generation bigint,
                    authorized_asset_ids uuid[])
            ),
            """;

    private static final String SINGLE_SCOPE = """
            WITH requested_scopes AS (
                SELECT
                    CAST(:spaceId AS uuid) AS space_id,
                    CAST(:batchId AS uuid) AS batch_id,
                    CAST(:generation AS bigint) AS generation,
                    CAST(:manifestFingerprint AS text) AS manifest_fingerprint,
                    CAST(:aclGeneration AS bigint) AS acl_generation,
                    CAST(:authorizedAssetIds AS uuid[]) AS authorized_asset_ids
            ),
            """;

    private static final String QUERY_BODY = """
            valid_scopes AS MATERIALIZED (
                SELECT requested.*
                FROM requested_scopes requested
                JOIN projection_publications publication
                  ON publication.batch_id = requested.batch_id
                 AND publication.organization_id = :organizationId
                 AND publication.workspace = 'default'
                 AND publication.collection_name = requested.space_id::text
                 AND publication.generation = requested.generation
                 AND publication.manifest_fingerprint = requested.manifest_fingerprint
                 AND publication.projections LIKE '%VECTOR%'
                 AND publication.projections LIKE '%GRAPH%'
            ),
            authorized_assets AS MATERIALIZED (
                SELECT scope.*, asset_id
                FROM valid_scopes scope
                CROSS JOIN LATERAL unnest(scope.authorized_asset_ids) asset_id
            ),
            visible_entity_contributions AS MATERIALIZED (
                SELECT
                    scope.space_id,
                    scope.batch_id,
                    scope.generation,
                    scope.manifest_fingerprint,
                    scope.acl_generation AS request_acl_generation,
                    contribution.contribution_id,
                    contribution.entity_id,
                    contribution.organization_id,
                    contribution.knowledge_asset_id,
                    contribution.source_revision_id,
                    contribution.chunk_id,
                    contribution.acl_snapshot_id,
                    contribution.acl_generation AS evidence_acl_generation
                FROM authorized_assets scope
                JOIN projection_graph_entity_contributions contribution
                  ON contribution.batch_id = scope.batch_id
                 AND contribution.organization_id = :organizationId
                 AND contribution.knowledge_asset_id = scope.asset_id
            ),
            visible_entities AS MATERIALIZED (
                SELECT DISTINCT space_id, batch_id, entity_id
                FROM visible_entity_contributions
            ),
            visible_relations AS MATERIALIZED (
                SELECT DISTINCT
                    scope.space_id,
                    scope.batch_id,
                    relation.relation_id,
                    relation.source_entity_id,
                    relation.target_entity_id
                FROM authorized_assets scope
                JOIN projection_graph_relation_contributions contribution
                  ON contribution.batch_id = scope.batch_id
                 AND contribution.organization_id = :organizationId
                 AND contribution.knowledge_asset_id = scope.asset_id
                JOIN projection_graph_relations relation
                  ON relation.batch_id = contribution.batch_id
                 AND relation.relation_id = contribution.relation_id
                JOIN visible_entities source_entity
                  ON source_entity.space_id = scope.space_id
                 AND source_entity.batch_id = relation.batch_id
                 AND source_entity.entity_id = relation.source_entity_id
                JOIN visible_entities target_entity
                  ON target_entity.space_id = scope.space_id
                 AND target_entity.batch_id = relation.batch_id
                 AND target_entity.entity_id = relation.target_entity_id
            ),
            vector_candidates AS (
                SELECT
                    scope.space_id,
                    scope.batch_id,
                    vector.subject_id::uuid AS entity_id,
                    vector.embedding::vector(1536)
                        <=> CAST(:queryVector AS vector(1536)) AS distance
                FROM authorized_assets scope
                JOIN projection_vector_records vector
                  ON vector.batch_id = scope.batch_id
                 AND vector.organization_id = :organizationId
                 AND vector.knowledge_asset_id = scope.asset_id
                 AND vector.embedding_profile_id = :embeddingProfileId
                 AND vector.vector_kind = 'ENTITY'
                 AND vector.dimensions = 1536
                JOIN visible_entities entity
                  ON entity.space_id = scope.space_id
                 AND entity.batch_id = vector.batch_id
                 AND entity.entity_id = vector.subject_id::uuid
            ),
            ranked_seeds AS (
                SELECT ranked.*
                FROM (
                    SELECT
                        candidate.*,
                        row_number() OVER (
                            PARTITION BY candidate.space_id
                            ORDER BY candidate.distance, candidate.entity_id) AS seed_rank
                    FROM vector_candidates candidate
                ) ranked
                WHERE ranked.seed_rank <= :seedLimit
                  AND ranked.distance <= 1.0 - :minimumSimilarity
            ),
            expanded_entities AS (
                SELECT space_id, batch_id, entity_id, distance
                FROM ranked_seeds
                UNION ALL
                SELECT
                    seed.space_id,
                    seed.batch_id,
                    CASE
                        WHEN relation.source_entity_id = seed.entity_id
                            THEN relation.target_entity_id
                        ELSE relation.source_entity_id
                    END AS entity_id,
                    seed.distance
                FROM ranked_seeds seed
                JOIN visible_relations relation
                  ON relation.space_id = seed.space_id
                 AND relation.batch_id = seed.batch_id
                 AND (relation.source_entity_id = seed.entity_id
                      OR relation.target_entity_id = seed.entity_id)
            ),
            candidate_entities AS (
                SELECT space_id, batch_id, entity_id, min(distance) AS distance
                FROM expanded_entities
                GROUP BY space_id, batch_id, entity_id
            ),
            globally_ranked AS (
                SELECT
                    candidate.*,
                    row_number() OVER (
                        ORDER BY candidate.distance, candidate.space_id, candidate.entity_id)
                        AS global_rank
                FROM candidate_entities candidate
            ),
            selected_entities AS (
                SELECT *
                FROM globally_ranked
                WHERE global_rank <= :globalLimit
            )
            SELECT
                contribution.space_id,
                contribution.batch_id,
                contribution.generation,
                contribution.manifest_fingerprint,
                contribution.request_acl_generation,
                selected.entity_id AS candidate_id,
                selected.global_rank,
                1.0 - selected.distance AS similarity,
                contribution.contribution_id,
                contribution.organization_id,
                contribution.knowledge_asset_id,
                contribution.source_revision_id,
                contribution.chunk_id,
                contribution.acl_snapshot_id,
                contribution.evidence_acl_generation
            FROM selected_entities selected
            JOIN visible_entity_contributions contribution
              ON contribution.space_id = selected.space_id
             AND contribution.batch_id = selected.batch_id
             AND contribution.entity_id = selected.entity_id
            ORDER BY
                selected.global_rank,
                contribution.space_id,
                selected.entity_id,
                contribution.contribution_id
            """;

    private static final String COMPOUND_SQL = COMPOUND_SCOPES + QUERY_BODY;
    private static final String PER_SPACE_SQL = SINGLE_SCOPE + QUERY_BODY;
    private static final Comparator<CandidateRow> ROW_ORDER = Comparator
            .comparingInt(CandidateRow::globalRank)
            .thenComparing(CandidateRow::spaceId)
            .thenComparing(CandidateRow::candidateId)
            .thenComparing(CandidateRow::contributionId);
    private static final Comparator<CandidateScore> SCORE_ORDER = Comparator
            .comparingDouble(CandidateScore::similarity)
            .reversed()
            // PostgreSQL orders UUIDs by their unsigned bytes. UUID.compareTo uses
            // signed longs, while canonical hexadecimal text preserves the store order.
            .thenComparing(score -> score.key().spaceId().toString())
            .thenComparing(score -> score.key().candidateId().toString());

    private final DataSource dataSource;

    AuthorizedMultiSnapshotQuery(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    QueryExecution execute(Request request) {
        validate(request);
        return query(COMPOUND_SQL, parameters(request));
    }

    QueryExecution execute(Request request, int statementTimeoutMillis) {
        validate(request);
        if (statementTimeoutMillis <= 0) {
            throw new IllegalArgumentException("statementTimeoutMillis must be positive");
        }
        return query(COMPOUND_SQL, parameters(request), statementTimeoutMillis);
    }

    QueryExecution executePerSpace(Request request) {
        validate(request);
        List<CandidateRow> candidates = new ArrayList<>();
        long connectionWaitNanos = 0;
        long queryNanos = 0;
        for (SnapshotScope scope : request.scopes()) {
            QueryExecution execution = query(PER_SPACE_SQL, parameters(request, scope));
            candidates.addAll(execution.rows());
            connectionWaitNanos += execution.connectionWaitNanos();
            queryNanos += execution.queryNanos();
        }
        return new QueryExecution(
                globalMerge(candidates, request.globalLimit()),
                connectionWaitNanos,
                queryNanos);
    }

    String explain(Request request) {
        return explain(request, 0);
    }

    String explain(Request request, int statementTimeoutMillis) {
        validate(request);
        QueryExecution execution = query(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + COMPOUND_SQL,
                parameters(request),
                statementTimeoutMillis,
                (resultSet, rowNumber) -> resultSet.getString(1));
        if (execution.explainRows().size() != 1) {
            throw new IllegalStateException("EXPLAIN did not return exactly one JSON plan");
        }
        return execution.explainRows().getFirst();
    }

    private QueryExecution query(String sql, MapSqlParameterSource parameters) {
        return query(sql, parameters, 0);
    }

    private QueryExecution query(
            String sql, MapSqlParameterSource parameters, int statementTimeoutMillis) {
        return query(sql, parameters, statementTimeoutMillis, (resultSet, rowNumber) -> new CandidateRow(
                resultSet.getObject("space_id", UUID.class),
                resultSet.getObject("batch_id", UUID.class),
                resultSet.getLong("generation"),
                resultSet.getString("manifest_fingerprint"),
                resultSet.getLong("request_acl_generation"),
                resultSet.getObject("candidate_id", UUID.class),
                resultSet.getInt("global_rank"),
                resultSet.getDouble("similarity"),
                resultSet.getObject("contribution_id", UUID.class),
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject("knowledge_asset_id", UUID.class),
                resultSet.getObject("source_revision_id", UUID.class),
                resultSet.getObject("chunk_id", UUID.class),
                resultSet.getObject("acl_snapshot_id", UUID.class),
                resultSet.getLong("evidence_acl_generation")));
    }

    private <T> QueryExecution query(
            String sql,
            MapSqlParameterSource parameters,
            org.springframework.jdbc.core.RowMapper<T> mapper) {
        return query(sql, parameters, 0, mapper);
    }

    private <T> QueryExecution query(
            String sql,
            MapSqlParameterSource parameters,
            int statementTimeoutMillis,
            org.springframework.jdbc.core.RowMapper<T> mapper) {
        long connectionStarted = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            long connectionWait = System.nanoTime() - connectionStarted;
            if (statementTimeoutMillis > 0) {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET statement_timeout = " + statementTimeoutMillis);
                }
            }
            var jdbc = new NamedParameterJdbcTemplate(
                    new SingleConnectionDataSource(connection, true));
            long queryStarted = System.nanoTime();
            List<T> rows = jdbc.query(sql, parameters, mapper);
            long queryDuration = System.nanoTime() - queryStarted;
            if (!rows.isEmpty() && rows.getFirst() instanceof CandidateRow) {
                @SuppressWarnings("unchecked")
                List<CandidateRow> candidateRows = (List<CandidateRow>) rows;
                return new QueryExecution(candidateRows, connectionWait, queryDuration);
            }
            @SuppressWarnings("unchecked")
            List<String> explainRows = (List<String>) rows;
            return QueryExecution.explain(explainRows, connectionWait, queryDuration);
        } catch (SQLException exception) {
            throw new IllegalStateException("multi-snapshot query connection failed", exception);
        }
    }

    private static List<CandidateRow> globalMerge(
            Collection<CandidateRow> perSpaceRows, int globalLimit) {
        Map<CandidateKey, CandidateScore> scores = new LinkedHashMap<>();
        for (CandidateRow row : perSpaceRows) {
            CandidateKey key = new CandidateKey(row.spaceId(), row.batchId(), row.candidateId());
            scores.merge(
                    key,
                    new CandidateScore(key, row.similarity()),
                    (left, right) -> left.similarity() >= right.similarity() ? left : right);
        }
        List<CandidateScore> ranked = scores.values().stream()
                .sorted(SCORE_ORDER)
                .limit(globalLimit)
                .toList();
        Map<CandidateKey, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < ranked.size(); index++) {
            ranks.put(ranked.get(index).key(), index + 1);
        }
        return perSpaceRows.stream()
                .filter(row -> ranks.containsKey(
                        new CandidateKey(row.spaceId(), row.batchId(), row.candidateId())))
                .map(row -> row.withGlobalRank(ranks.get(
                        new CandidateKey(row.spaceId(), row.batchId(), row.candidateId()))))
                .sorted(ROW_ORDER)
                .toList();
    }

    private static MapSqlParameterSource parameters(Request request) {
        return commonParameters(request).addValue("scopes", scopesJson(request.scopes()));
    }

    private static MapSqlParameterSource parameters(Request request, SnapshotScope scope) {
        return commonParameters(request)
                .addValue("spaceId", scope.spaceId())
                .addValue("batchId", scope.batchId())
                .addValue("generation", scope.generation())
                .addValue("manifestFingerprint", scope.manifestFingerprint())
                .addValue("aclGeneration", scope.aclGeneration())
                .addValue(
                        "authorizedAssetIds",
                        scope.authorizedAssetIds().toArray(UUID[]::new));
    }

    private static MapSqlParameterSource commonParameters(Request request) {
        return new MapSqlParameterSource()
                .addValue("organizationId", request.organizationId())
                .addValue("embeddingProfileId", request.embeddingProfileId())
                .addValue("queryVector", request.queryVector())
                .addValue("seedLimit", request.seedLimit())
                .addValue("globalLimit", request.globalLimit())
                .addValue("minimumSimilarity", request.minimumSimilarity());
    }

    private static String scopesJson(List<SnapshotScope> scopes) {
        return scopes.stream()
                .map(scope -> "{\"space_id\":\"" + scope.spaceId()
                        + "\",\"batch_id\":\"" + scope.batchId()
                        + "\",\"generation\":" + scope.generation()
                        + ",\"manifest_fingerprint\":\"" + scope.manifestFingerprint()
                        + "\",\"acl_generation\":" + scope.aclGeneration()
                        + ",\"authorized_asset_ids\":["
                        + scope.authorizedAssetIds().stream()
                                .map(id -> "\"" + id + "\"")
                                .collect(Collectors.joining(","))
                        + "]}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static void validate(Request request) {
        Objects.requireNonNull(request, "request");
        List<UUID> orderedSpaceIds = request.scopes().stream()
                .map(SnapshotScope::spaceId)
                .toList();
        List<UUID> sortedSpaceIds = orderedSpaceIds.stream().sorted().toList();
        if (!orderedSpaceIds.equals(sortedSpaceIds)
                || new LinkedHashSet<>(orderedSpaceIds).size() != orderedSpaceIds.size()) {
            throw new IllegalArgumentException("snapshot scopes must be unique and sorted by space id");
        }
    }

    void validateRows(Request request, List<CandidateRow> rows) {
        validate(request);
        Map<UUID, SnapshotScope> scopes = request.scopes().stream()
                .collect(Collectors.toMap(SnapshotScope::spaceId, Function.identity()));
        for (CandidateRow row : rows) {
            SnapshotScope scope = scopes.get(row.spaceId());
            if (scope == null
                    || !scope.batchId().equals(row.batchId())
                    || scope.generation() != row.generation()
                    || !scope.manifestFingerprint().equals(row.manifestFingerprint())
                    || scope.aclGeneration() != row.requestAclGeneration()
                    || !request.organizationId().equals(row.organizationId())
                    || !scope.authorizedAssetIds().contains(row.knowledgeAssetId())) {
                throw new IllegalStateException("query returned identity outside its authorized tuple set");
            }
        }
    }

    record SnapshotScope(
            UUID spaceId,
            UUID batchId,
            long generation,
            String manifestFingerprint,
            long aclGeneration,
            List<UUID> authorizedAssetIds) {

        SnapshotScope {
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(batchId, "batchId");
            if (generation <= 0 || aclGeneration < 0) {
                throw new IllegalArgumentException("scope generations must be valid");
            }
            manifestFingerprint = requireText(manifestFingerprint, "manifestFingerprint");
            authorizedAssetIds = List.copyOf(
                    Objects.requireNonNull(authorizedAssetIds, "authorizedAssetIds"));
            if (authorizedAssetIds.isEmpty()
                    || authorizedAssetIds.size() != Set.copyOf(authorizedAssetIds).size()) {
                throw new IllegalArgumentException(
                        "authorizedAssetIds must be non-empty and unique");
            }
        }
    }

    record Request(
            UUID organizationId,
            UUID embeddingProfileId,
            String queryVector,
            int seedLimit,
            int globalLimit,
            double minimumSimilarity,
            List<SnapshotScope> scopes) {

        Request {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
            queryVector = requireText(queryVector, "queryVector");
            if (seedLimit <= 0 || globalLimit <= 0) {
                throw new IllegalArgumentException("query limits must be positive");
            }
            if (!Double.isFinite(minimumSimilarity)
                    || minimumSimilarity < -1.0
                    || minimumSimilarity > 1.0) {
                throw new IllegalArgumentException("minimumSimilarity must be between -1 and 1");
            }
            scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
            if (scopes.isEmpty()) {
                throw new IllegalArgumentException("scopes must not be empty");
            }
        }
    }

    record CandidateRow(
            UUID spaceId,
            UUID batchId,
            long generation,
            String manifestFingerprint,
            long requestAclGeneration,
            UUID candidateId,
            int globalRank,
            double similarity,
            UUID contributionId,
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            UUID chunkId,
            UUID aclSnapshotId,
            long evidenceAclGeneration) {

        CandidateRow {
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(batchId, "batchId");
            Objects.requireNonNull(candidateId, "candidateId");
            Objects.requireNonNull(contributionId, "contributionId");
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
            Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
            Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        }

        CandidateRow withGlobalRank(int rank) {
            return new CandidateRow(
                    spaceId,
                    batchId,
                    generation,
                    manifestFingerprint,
                    requestAclGeneration,
                    candidateId,
                    rank,
                    similarity,
                    contributionId,
                    organizationId,
                    knowledgeAssetId,
                    sourceRevisionId,
                    chunkId,
                    aclSnapshotId,
                    evidenceAclGeneration);
        }
    }

    record QueryExecution(
            List<CandidateRow> rows,
            List<String> explainRows,
            long connectionWaitNanos,
            long queryNanos) {

        QueryExecution(List<CandidateRow> rows, long connectionWaitNanos, long queryNanos) {
            this(rows, List.of(), connectionWaitNanos, queryNanos);
        }

        QueryExecution {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
            explainRows = List.copyOf(Objects.requireNonNull(explainRows, "explainRows"));
            if (connectionWaitNanos < 0 || queryNanos < 0) {
                throw new IllegalArgumentException("query timings must not be negative");
            }
        }

        static QueryExecution explain(
                List<String> explainRows, long connectionWaitNanos, long queryNanos) {
            return new QueryExecution(
                    List.of(), explainRows, connectionWaitNanos, queryNanos);
        }
    }

    private record CandidateKey(UUID spaceId, UUID batchId, UUID candidateId) {}

    private record CandidateScore(CandidateKey key, double similarity) {}

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
