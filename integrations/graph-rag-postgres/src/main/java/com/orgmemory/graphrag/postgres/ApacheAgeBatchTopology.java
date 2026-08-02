package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource.IncidentRelationPage;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Transactional, publication-batch-pinned Apache AGE topology. */
final class ApacheAgeBatchTopology {

    private static final String ENTITY_LABEL = "base";
    private static final String RELATION_LABEL = "DIRECTED";
    private static final String MARKER_LABEL = "batch_marker";

    private final NamedParameterJdbcTemplate jdbc;
    private final int pageSize;

    ApacheAgeBatchTopology(
            NamedParameterJdbcTemplate jdbc,
            int pageSize) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.pageSize = PostgresBatchOperations.requireBatchSize(pageSize);
        requireAvailable();
    }

    void prepareGraph(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId");
        configureSession();
        String graphName = graphName(organizationId);
        lock(graphName);
        ensureGraph(graphName);
    }

    void rebuild(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        configureSession();
        String graphName = graphName(batch.namespace().organizationId());
        lock(graphName);
        if (!graphExists(graphName)) {
            throw new IllegalStateException(
                    "Apache AGE graph was not prepared for organization "
                            + batch.namespace().organizationId());
        }
        deleteBatch(graphName, batch.id());
        copyEntities(graphName, batch.id());
        copyRelations(graphName, batch.id());
        createReadyMarker(graphName, batch);
    }

    void discard(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        configureSession();
        String graphName = graphName(batch.namespace().organizationId());
        lock(graphName);
        if (graphExists(graphName)) {
            deleteBatch(graphName, batch.id());
        }
    }

    void requireReady(ProjectionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        configureSession();
        String graphName = graphName(snapshot.namespace().organizationId());
        if (!graphExists(graphName)) {
            throw new IllegalStateException(
                    "Apache AGE graph is unavailable for published batch "
                            + snapshot.batchId());
        }
        String cypher = """
                MATCH (marker:batch_marker {batch_id: %s})
                RETURN marker.manifest_fingerprint AS manifest_fingerprint,
                       marker.generation AS generation
                """.formatted(cypherString(snapshot.batchId().toString()));
        List<ReadyMarker> markers = jdbc.getJdbcTemplate().query(
                cypherSql(graphName, cypher,
                        "manifest_fingerprint ag_catalog.agtype, generation ag_catalog.agtype"),
                (resultSet, rowNumber) -> new ReadyMarker(
                        parseAgtypeString(resultSet.getString("manifest_fingerprint")),
                        parseAgtypeLong(resultSet.getString("generation"))));
        if (markers.size() != 1) {
            throw new IllegalStateException(
                    "Apache AGE ready marker is missing or duplicated for published batch "
                            + snapshot.batchId());
        }
        ReadyMarker marker = markers.getFirst();
        if (!snapshot.manifestFingerprint().equals(marker.manifestFingerprint())
                || snapshot.generation() != marker.generation()) {
            throw new IllegalStateException(
                    "Apache AGE ready marker does not match published batch "
                            + snapshot.batchId());
        }
    }

    IncidentRelationPage loadIncidentRelationPage(
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            Collection<UUID> authorizedAssetIds,
            UUID afterRelationId,
            int pageSize) {
        List<UUID> ids = canonicalIds(entityIds);
        List<UUID> assets = canonicalIds(authorizedAssetIds);
        requirePageSize(pageSize);
        if (ids.isEmpty() || assets.isEmpty()) {
            return new IncidentRelationPage(List.of(), null);
        }
        String cursorPredicate = afterRelationId == null
                ? ""
                : "AND relation.relation_id > "
                        + cypherString(afterRelationId.toString());
        String cypher = """
                MATCH (source:base)-[relation:DIRECTED]->(target:base)
                WHERE relation.batch_id = %s
                  AND source.batch_id = %s
                  AND target.batch_id = %s
                  AND (source.entity_id IN %s OR target.entity_id IN %s)
                  AND relation.knowledge_asset_id IN %s
                  %s
                RETURN DISTINCT relation.relation_id AS relation_id,
                       source.entity_id AS source_entity_id,
                       target.entity_id AS target_entity_id,
                       relation.orientation AS orientation
                ORDER BY relation_id
                LIMIT %d
                """.formatted(
                cypherString(snapshot.batchId().toString()),
                cypherString(snapshot.batchId().toString()),
                cypherString(snapshot.batchId().toString()),
                cypherStrings(ids),
                cypherStrings(ids),
                cypherStrings(assets),
                cursorPredicate,
                pageSize + 1);
        String graphName = graphName(snapshot.namespace().organizationId());
        List<CanonicalRelation> fetched = jdbc.getJdbcTemplate().query(
                cypherSql(
                        graphName,
                        cypher,
                        "relation_id ag_catalog.agtype, "
                                + "source_entity_id ag_catalog.agtype, "
                                + "target_entity_id ag_catalog.agtype, "
                                + "orientation ag_catalog.agtype"),
                (resultSet, rowNumber) -> new CanonicalRelation(
                        UUID.fromString(parseAgtypeString(
                                resultSet.getString("relation_id"))),
                        UUID.fromString(parseAgtypeString(
                                resultSet.getString("source_entity_id"))),
                        UUID.fromString(parseAgtypeString(
                                resultSet.getString("target_entity_id"))),
                        RelationOrientation.valueOf(parseAgtypeString(
                                resultSet.getString("orientation")))));
        boolean hasMore = fetched.size() > pageSize;
        List<CanonicalRelation> page = hasMore
                ? List.copyOf(fetched.subList(0, pageSize))
                : List.copyOf(fetched);
        return new IncidentRelationPage(
                page,
                hasMore ? page.getLast().id() : null);
    }

    private void requireAvailable() {
        try {
            Boolean installed = jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_extension WHERE extname = 'age'
                    )
                    """, new MapSqlParameterSource(), Boolean.class);
            if (!Boolean.TRUE.equals(installed)) {
                throw unavailable("the age extension is not installed", null);
            }
            String preload = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT current_setting('session_preload_libraries')",
                    String.class);
            boolean agePreloaded = preload != null
                    && java.util.Arrays.stream(preload.split(","))
                            .map(String::strip)
                            .anyMatch("age"::equalsIgnoreCase);
            if (!agePreloaded) {
                throw unavailable(
                        "session_preload_libraries does not include age", null);
            }
            jdbc.getJdbcTemplate().queryForObject(
                    "SELECT count(*) FROM ag_catalog.ag_graph", Long.class);
        } catch (DataAccessException exception) {
            throw unavailable(
                    "the AGE catalog is not readable; verify extension preload and runtime privileges",
                    exception);
        }
    }

    private static IllegalStateException unavailable(
            String detail,
            Exception cause) {
        String message = "Apache AGE topology backend was selected but " + detail;
        return cause == null
                ? new IllegalStateException(message)
                : new IllegalStateException(message, cause);
    }

    private void copyEntities(String graphName, UUID batchId) {
        UUID after = null;
        while (true) {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("after", after)
                    .addValue("limit", pageSize);
            List<UUID> page = jdbc.query("""
                    SELECT entity_id
                    FROM projection_graph_entities
                    WHERE batch_id = :batchId
                      AND (:after::uuid IS NULL OR entity_id > :after)
                    ORDER BY entity_id
                    LIMIT :limit
                    """, parameters,
                    (resultSet, rowNumber) ->
                            resultSet.getObject("entity_id", UUID.class));
            page.forEach(entityId -> upsertEntity(graphName, batchId, entityId));
            if (page.size() < pageSize) {
                return;
            }
            after = page.getLast();
        }
    }

    private void copyRelations(String graphName, UUID batchId) {
        UUID after = null;
        while (true) {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("after", after)
                    .addValue("limit", pageSize);
            List<TopologyRelation> page = jdbc.query("""
                    SELECT contribution.contribution_id,
                           contribution.relation_id,
                           contribution.knowledge_asset_id,
                           relation.source_entity_id,
                           relation.target_entity_id,
                           relation.orientation
                    FROM projection_graph_relation_contributions contribution
                    JOIN projection_graph_relations relation
                      ON relation.batch_id = contribution.batch_id
                     AND relation.relation_id = contribution.relation_id
                    WHERE contribution.batch_id = :batchId
                      AND (:after::uuid IS NULL OR contribution.contribution_id > :after)
                    ORDER BY contribution.contribution_id
                    LIMIT :limit
                    """, parameters,
                    (resultSet, rowNumber) -> new TopologyRelation(
                            resultSet.getObject("contribution_id", UUID.class),
                            resultSet.getObject("relation_id", UUID.class),
                            resultSet.getObject("knowledge_asset_id", UUID.class),
                            resultSet.getObject("source_entity_id", UUID.class),
                            resultSet.getObject("target_entity_id", UUID.class),
                            RelationOrientation.valueOf(
                                    resultSet.getString("orientation"))));
            page.forEach(relation -> upsertRelation(graphName, batchId, relation));
            if (page.size() < pageSize) {
                return;
            }
            after = page.getLast().contributionId();
        }
    }

    private void configureSession() {
        jdbc.getJdbcTemplate()
                .execute("SET LOCAL search_path = ag_catalog, \"$user\", public");
    }

    private void lock(String graphName) {
        jdbc.query("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended(CAST(:lockKey AS text), 0)
                )
                """,
                new MapSqlParameterSource("lockKey", graphName),
                (RowCallbackHandler) resultSet -> {
                    // The lock is held by the surrounding staging transaction.
                });
    }

    private void ensureGraph(String graphName) {
        if (graphExists(graphName)) {
            return;
        }
        consumeVoid(
                "SELECT ag_catalog.create_graph(CAST(:graphName AS name))",
                new MapSqlParameterSource("graphName", graphName));
        for (String label : List.of(ENTITY_LABEL, RELATION_LABEL, MARKER_LABEL)) {
            String function = RELATION_LABEL.equals(label)
                    ? "create_elabel"
                    : "create_vlabel";
            consumeVoid(
                    "SELECT ag_catalog.%s(CAST(:graphName AS cstring), CAST(:label AS cstring))"
                            .formatted(function),
                    new MapSqlParameterSource()
                            .addValue("graphName", graphName)
                            .addValue("label", label));
        }
        createIndexes(graphName);
    }

    private boolean graphExists(String graphName) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM ag_catalog.ag_graph WHERE name = :graphName
                )
                """, new MapSqlParameterSource("graphName", graphName), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private void createIndexes(String graphName) {
        String schema = quoteIdentifier(graphName);
        jdbc.getJdbcTemplate().execute("""
                CREATE INDEX IF NOT EXISTS graph_base_batch_entity_idx
                ON %s.%s (
                    ag_catalog.agtype_access_operator(
                        properties, '"batch_id"'::ag_catalog.agtype),
                    ag_catalog.agtype_access_operator(
                        properties, '"entity_id"'::ag_catalog.agtype)
                )
                """.formatted(schema, quoteIdentifier(ENTITY_LABEL)));
        jdbc.getJdbcTemplate().execute("""
                CREATE INDEX IF NOT EXISTS graph_directed_batch_relation_idx
                ON %s.%s (
                    ag_catalog.agtype_access_operator(
                        properties, '"batch_id"'::ag_catalog.agtype),
                    ag_catalog.agtype_access_operator(
                        properties, '"relation_id"'::ag_catalog.agtype)
                )
                """.formatted(schema, quoteIdentifier(RELATION_LABEL)));
        jdbc.getJdbcTemplate().execute("""
                CREATE INDEX IF NOT EXISTS graph_batch_marker_idx
                ON %s.%s (
                    ag_catalog.agtype_access_operator(
                        properties, '"batch_id"'::ag_catalog.agtype)
                )
                """.formatted(schema, quoteIdentifier(MARKER_LABEL)));
    }

    private void deleteBatch(String graphName, UUID batchId) {
        String id = cypherString(batchId.toString());
        executeCypher(graphName, """
                MATCH ()-[relation:DIRECTED]->()
                WHERE relation.batch_id = %s
                DELETE relation
                RETURN count(relation)
                """.formatted(id), "deleted_count ag_catalog.agtype");
        executeCypher(graphName, """
                MATCH (node)
                WHERE node.batch_id = %s
                DETACH DELETE node
                RETURN count(node)
                """.formatted(id), "deleted_count ag_catalog.agtype");
    }

    private void upsertEntity(
            String graphName,
            UUID batchId,
            UUID entityId) {
        executeCypher(graphName, """
                MERGE (entity:base {batch_id: %s, entity_id: %s})
                RETURN entity
                """.formatted(
                cypherString(batchId.toString()),
                cypherString(entityId.toString())),
                "entity ag_catalog.agtype");
    }

    private void upsertRelation(
            String graphName,
            UUID batchId,
            TopologyRelation relation) {
        executeCypher(graphName, """
                MATCH (source:base {batch_id: %s, entity_id: %s})
                MATCH (target:base {batch_id: %s, entity_id: %s})
                MERGE (source)-[edge:DIRECTED {
                    batch_id: %s,
                    contribution_id: %s
                }]->(target)
                SET edge.relation_id = %s,
                    edge.knowledge_asset_id = %s,
                    edge.orientation = %s
                RETURN edge
                """.formatted(
                cypherString(batchId.toString()),
                cypherString(relation.sourceEntityId().toString()),
                cypherString(batchId.toString()),
                cypherString(relation.targetEntityId().toString()),
                cypherString(batchId.toString()),
                cypherString(relation.contributionId().toString()),
                cypherString(relation.relationId().toString()),
                cypherString(relation.knowledgeAssetId().toString()),
                cypherString(relation.orientation().name())),
                "edge ag_catalog.agtype");
    }

    private void createReadyMarker(String graphName, ProjectionBatch batch) {
        executeCypher(graphName, """
                CREATE (marker:batch_marker {
                    batch_id: %s,
                    manifest_fingerprint: %s,
                    generation: %d,
                    claim_epoch: %d
                })
                RETURN marker
                """.formatted(
                cypherString(batch.id().toString()),
                cypherString(batch.manifestFingerprint()),
                batch.generation(),
                batch.claimEpoch()),
                "marker ag_catalog.agtype");
    }

    private void executeCypher(
            String graphName,
            String cypher,
            String resultDefinition) {
        jdbc.getJdbcTemplate().query(
                cypherSql(graphName, cypher, resultDefinition),
                (RowCallbackHandler) resultSet -> {
                    // Consuming the row executes the AGE mutation.
                });
    }

    private void consumeVoid(String sql, MapSqlParameterSource parameters) {
        jdbc.query(sql, parameters, (RowCallbackHandler) resultSet -> {
            // AGE setup functions return void.
        });
    }

    private static String cypherSql(
            String graphName,
            String cypher,
            String resultDefinition) {
        return "SELECT * FROM ag_catalog.cypher("
                + sqlString(graphName)
                + "::name, "
                + dollarQuote(cypher)
                + "::cstring) AS ("
                + resultDefinition
                + ")";
    }

    private static List<UUID> canonicalIds(Collection<UUID> values) {
        Objects.requireNonNull(values, "values");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, "ids must not contain null"))
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    private static void requirePageSize(int value) {
        if (value < 1 || value > 10_000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 10000");
        }
    }

    private static String graphName(UUID organizationId) {
        return "orgmemory_" + organizationId.toString().replace("-", "");
    }

    private static String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("unsafe PostgreSQL identifier");
        }
        return '"' + identifier + '"';
    }

    private static String sqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String cypherStrings(Collection<UUID> values) {
        return values.stream()
                .map(value -> cypherString(value.toString()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String cypherString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (codePoint < 0x20) {
                        escaped.append("\\u%04x".formatted(codePoint));
                    } else {
                        escaped.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return escaped.append('"').toString();
    }

    private static String parseAgtypeString(String value) {
        if (value == null || value.length() < 2
                || value.charAt(0) != '"'
                || value.charAt(value.length() - 1) != '"') {
            throw new IllegalStateException("Apache AGE returned a non-string property");
        }
        return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static long parseAgtypeLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.split("::", 2)[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Apache AGE returned a non-integer property", exception);
        }
    }

    private static String dollarQuote(String value) {
        String tag = "$ORGMEMORY_AGE$";
        int suffix = 0;
        while (value.contains(tag)) {
            suffix++;
            tag = "$ORGMEMORY_AGE_" + suffix + "$";
        }
        return tag + value + tag;
    }

    private record ReadyMarker(String manifestFingerprint, long generation) {}

    private record TopologyRelation(
            UUID contributionId,
            UUID relationId,
            UUID knowledgeAssetId,
            UUID sourceEntityId,
            UUID targetEntityId,
            RelationOrientation orientation) {}
}
