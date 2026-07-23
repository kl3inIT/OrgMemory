package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Apache AGE topology projection compatible with LightRAG's PostgreSQL graph shape.
 *
 * <p>Only canonical identity and evidence identifiers enter AGE. Descriptions, ACLs and
 * provenance remain in the relational contribution ledger and are rechecked there before
 * candidates can affect retrieval.
 */
public final class ApacheAgeGraphTopologyProjection {

    private static final String ENTITY_LABEL = "base";
    private static final String RELATION_LABEL = "DIRECTED";

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean available;

    public ApacheAgeGraphTopologyProjection(
            NamedParameterJdbcTemplate jdbc,
            ApacheAgeMode mode) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(mode, "mode");
        this.available = mode != ApacheAgeMode.DISABLED && detectAvailability();
        if (mode == ApacheAgeMode.REQUIRED && !available) {
            throw new IllegalStateException(
                    "Apache AGE is required but the age extension is not installed");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void replaceRevision(GraphRevisionContributions contributions) {
        Objects.requireNonNull(contributions, "contributions");
        if (!available) {
            return;
        }
        configureSession();
        String graphName = ensureGraph(contributions.organizationId());
        deleteRevisionEdges(graphName, contributions.sourceRevisionId());

        Map<UUID, CanonicalEntity> entities = contributions.entities().stream()
                .map(EntityContribution::entity)
                .collect(Collectors.toMap(
                        CanonicalEntity::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        entities.values().stream()
                .sorted(Comparator.comparing(CanonicalEntity::id))
                .forEach(entity -> upsertEntity(graphName, entity));
        contributions.relations().stream()
                .sorted(Comparator.comparing(RelationContribution::id))
                .forEach(contribution -> upsertRelation(graphName, contribution));
    }

    public void removeRevision(UUID organizationId, UUID sourceRevisionId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        if (!available) {
            return;
        }
        configureSession();
        String graphName = graphName(organizationId);
        if (!graphExists(graphName)) {
            return;
        }
        deleteRevisionEdges(graphName, sourceRevisionId);
    }

    public List<UUID> expandEntityIds(
            UUID organizationId,
            Collection<UUID> seedEntityIds,
            Collection<UUID> authorizedAssetIds,
            int maximumDepth,
            int limit) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(seedEntityIds, "seedEntityIds");
        Objects.requireNonNull(authorizedAssetIds, "authorizedAssetIds");
        if (!available || seedEntityIds.isEmpty() || authorizedAssetIds.isEmpty() || limit == 0) {
            return List.of();
        }
        if (maximumDepth < 1 || maximumDepth > 5 || limit < 0 || limit > 10_000) {
            throw new IllegalArgumentException("unsafe AGE traversal bounds");
        }
        configureSession();
        String graphName = graphName(organizationId);
        if (!graphExists(graphName)) {
            return List.of();
        }
        String seeds = cypherStrings(seedEntityIds);
        String assets = cypherStrings(authorizedAssetIds);
        String cypher = java.util.stream.IntStream.rangeClosed(1, maximumDepth)
                .mapToObj(depth -> traversalBranch(depth, seeds, assets, limit))
                .collect(Collectors.joining("\nUNION\n"));
        String sql = "SELECT * FROM ag_catalog.cypher("
                + sqlString(graphName)
                + "::name, "
                + dollarQuote(cypher)
                + "::cstring) AS (entity_id ag_catalog.agtype)";
        return jdbc.getJdbcTemplate().query(
                sql,
                (resultSet, rowNumber) -> UUID.fromString(
                        parseAgtypeString(resultSet.getString("entity_id"))))
                .stream()
                .distinct()
                .sorted()
                .limit(limit)
                .toList();
    }

    private boolean detectAvailability() {
        Boolean installed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_extension
                    WHERE extname = 'age'
                )
                """, new MapSqlParameterSource(), Boolean.class);
        return Boolean.TRUE.equals(installed);
    }

    private void configureSession() {
        jdbc.getJdbcTemplate().execute("LOAD 'age'");
        jdbc.getJdbcTemplate()
                .execute("SET LOCAL search_path = ag_catalog, \"$user\", public");
    }

    private String ensureGraph(UUID organizationId) {
        String graphName = graphName(organizationId);
        consumeVoid("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended(CAST(:graphName AS text), 0)
                )
                """, new MapSqlParameterSource("graphName", graphName));
        if (graphExists(graphName)) {
            return graphName;
        }
        jdbc.getJdbcTemplate().execute("""
                DO $orgmemory$
                BEGIN
                    PERFORM ag_catalog.create_graph(%1$s);
                EXCEPTION WHEN OTHERS THEN
                    IF NOT EXISTS (
                        SELECT 1
                        FROM ag_catalog.ag_graph
                        WHERE name = %1$s
                    ) THEN
                        RAISE;
                    END IF;
                END
                $orgmemory$
                """.formatted(sqlString(graphName)));
        consumeVoid("""
                SELECT create_vlabel(
                    CAST(:graphName AS cstring),
                    CAST(:labelName AS cstring)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("graphName", graphName)
                        .addValue("labelName", ENTITY_LABEL));
        consumeVoid("""
                SELECT create_elabel(
                    CAST(:graphName AS cstring),
                    CAST(:labelName AS cstring)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("graphName", graphName)
                        .addValue("labelName", RELATION_LABEL));
        createIndexes(graphName);
        return graphName;
    }

    private boolean graphExists(String graphName) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM ag_catalog.ag_graph
                    WHERE name = :graphName
                )
                """, new MapSqlParameterSource("graphName", graphName), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private void createIndexes(String graphName) {
        String schema = quoteIdentifier(graphName);
        jdbc.getJdbcTemplate().execute("""
                CREATE INDEX IF NOT EXISTS graph_base_entity_id_idx
                ON %s.%s (
                    ag_catalog.agtype_access_operator(
                        properties,
                        '"entity_id"'::ag_catalog.agtype
                    )
                )
                """.formatted(schema, quoteIdentifier(ENTITY_LABEL)));
        jdbc.getJdbcTemplate().execute("""
                CREATE INDEX IF NOT EXISTS graph_directed_start_end_idx
                ON %s.%s (start_id, end_id)
                """.formatted(schema, quoteIdentifier(RELATION_LABEL)));
        jdbc.getJdbcTemplate().execute("""
                CREATE INDEX IF NOT EXISTS graph_directed_contribution_idx
                ON %s.%s (
                    ag_catalog.agtype_access_operator(
                        properties,
                        '"contribution_id"'::ag_catalog.agtype
                    )
                )
                """.formatted(schema, quoteIdentifier(RELATION_LABEL)));
    }

    private void deleteRevisionEdges(String graphName, UUID sourceRevisionId) {
        String cypher = """
                MATCH ()-[relation:DIRECTED]->()
                WHERE relation.source_revision_id = %s
                DELETE relation
                RETURN count(relation)
                """.formatted(cypherString(sourceRevisionId.toString()));
        executeCypher(graphName, cypher, "deleted_count agtype");
    }

    private void upsertEntity(String graphName, CanonicalEntity entity) {
        String cypher = """
                MERGE (entity:base {entity_id: %s})
                SET entity.normalized_name = %s,
                    entity.entity_type = %s
                RETURN entity
                """.formatted(
                cypherString(entity.id().toString()),
                cypherString(entity.normalizedName()),
                cypherString(entity.type()));
        executeCypher(graphName, cypher, "entity agtype");
    }

    private void upsertRelation(String graphName, RelationContribution contribution) {
        CanonicalRelation relation = contribution.relation();
        String cypher = """
                MATCH (source:base {entity_id: %s})
                MATCH (target:base {entity_id: %s})
                MERGE (source)-[relation:DIRECTED {contribution_id: %s}]->(target)
                SET relation.relation_id = %s,
                    relation.source_revision_id = %s,
                    relation.knowledge_asset_id = %s,
                    relation.projection_generation = %d,
                    relation.relation_type = %s,
                    relation.orientation = %s
                RETURN relation
                """.formatted(
                cypherString(relation.sourceEntityId().toString()),
                cypherString(relation.targetEntityId().toString()),
                cypherString(contribution.id().toString()),
                cypherString(relation.id().toString()),
                cypherString(contribution.provenance().sourceRevisionId().toString()),
                cypherString(contribution.provenance().knowledgeAssetId().toString()),
                contribution.provenance().projectionGeneration(),
                cypherString(relation.type()),
                cypherString(relation.orientation().name()));
        executeCypher(graphName, cypher, "relation agtype");
    }

    private void executeCypher(String graphName, String cypher, String resultDefinition) {
        String sql = "SELECT * FROM ag_catalog.cypher("
                + sqlString(graphName)
                + "::name, "
                + dollarQuote(cypher)
                + "::cstring) AS ("
                + resultDefinition
                + ")";
        jdbc.getJdbcTemplate().query(sql, (RowCallbackHandler) resultSet -> {
            // Consume all AGE rows; mutation success is the contract.
        });
    }

    private void consumeVoid(String sql, MapSqlParameterSource parameters) {
        jdbc.query(sql, parameters, (RowCallbackHandler) resultSet -> {
            // AGE setup functions return void.
        });
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

    private static String cypherStrings(Collection<UUID> values) {
        return values.stream()
                .sorted()
                .map(value -> cypherString(value.toString()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String traversalBranch(
            int depth, String seeds, String assets, int limit) {
        StringBuilder pattern = new StringBuilder("(seed:base)");
        StringBuilder edgePredicates = new StringBuilder();
        for (int hop = 1; hop <= depth; hop++) {
            String edge = "relation" + hop;
            String node = hop == depth ? "neighbor" : "node" + hop;
            pattern.append("-[").append(edge).append(":DIRECTED]-(")
                    .append(node).append(":base)");
            edgePredicates.append("\n  AND ")
                    .append(edge)
                    .append(".knowledge_asset_id IN ")
                    .append(assets);
        }
        return """
                MATCH %s
                WHERE seed.entity_id IN %s
                  AND NOT neighbor.entity_id IN %s%s
                RETURN DISTINCT neighbor.entity_id AS entity_id
                LIMIT %d
                """.formatted(pattern, seeds, seeds, edgePredicates, limit);
    }

    private static String parseAgtypeString(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '"'
                || value.charAt(value.length() - 1) != '"') {
            throw new IllegalStateException("AGE returned a non-string entity identifier");
        }
        return value.substring(1, value.length() - 1);
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
}
