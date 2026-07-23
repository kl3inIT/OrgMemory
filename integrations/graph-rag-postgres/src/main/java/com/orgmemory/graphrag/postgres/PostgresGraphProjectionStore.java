package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedGraphScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.ContributionEmbedding;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphEmbeddingIndex;
import com.orgmemory.graphrag.port.GraphProjectionReader;
import com.orgmemory.graphrag.port.GraphProjectionWriter;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.port.GraphRevisionEmbeddings;
import com.orgmemory.graphrag.port.GraphSeedIndex;
import com.orgmemory.graphrag.port.GraphTopologyCandidateIndex;
import com.orgmemory.graphrag.query.RankedItem;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL implementation of the secure graph projection ports.
 *
 * <p>The authorized asset set is resolved before this adapter is called. Every query still
 * applies that set before ranking, aggregation or LIMIT and joins the published projection
 * head so stale/incomplete generations cannot contribute to an answer.
 */
public final class PostgresGraphProjectionStore
        implements GraphProjectionReader,
                GraphProjectionWriter,
                GraphSeedIndex,
                GraphEmbeddingIndex,
                GraphTopologyCandidateIndex {

    private static final String VISIBLE_ENTITY_CONTRIBUTIONS = """
            visible_entity_contributions AS (
                SELECT ec.*
                FROM graph_entity_contributions ec
                JOIN graph_projection_heads head
                  ON head.organization_id = ec.organization_id
                 AND head.source_revision_id = ec.source_revision_id
                 AND head.knowledge_asset_id = ec.knowledge_asset_id
                 AND head.projection_generation = ec.projection_generation
                WHERE ec.organization_id = :organizationId
                  AND ec.knowledge_asset_id IN (:authorizedAssetIds)
            )
            """;

    private static final String VISIBLE_RELATION_CONTRIBUTIONS = """
            visible_relation_contributions AS (
                SELECT rc.*
                FROM graph_relation_contributions rc
                JOIN graph_projection_heads head
                  ON head.organization_id = rc.organization_id
                 AND head.source_revision_id = rc.source_revision_id
                 AND head.knowledge_asset_id = rc.knowledge_asset_id
                 AND head.projection_generation = rc.projection_generation
                JOIN graph_relations relation
                  ON relation.organization_id = rc.organization_id
                 AND relation.id = rc.relation_id
                WHERE rc.organization_id = :organizationId
                  AND rc.knowledge_asset_id IN (:authorizedAssetIds)
                  AND EXISTS (
                      SELECT 1
                      FROM visible_entity_contributions source_evidence
                      WHERE source_evidence.entity_id = relation.source_entity_id
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM visible_entity_contributions target_evidence
                      WHERE target_evidence.entity_id = relation.target_entity_id
                  )
            )
            """;

    private static final String ENTITY_CONTRIBUTION_COLUMNS = """
            ec.id AS contribution_id,
            entity.id AS entity_id,
            entity.normalized_name,
            entity.entity_type,
            ec.description,
            ec.organization_id,
            ec.knowledge_asset_id,
            ec.source_revision_id,
            ec.chunk_id,
            ec.acl_snapshot_id,
            ec.acl_generation,
            ec.projection_generation,
            ec.extractor_provider,
            ec.extractor_model,
            ec.prompt_version,
            ec.confidence,
            ec.extracted_at
            """;

    private static final String RELATION_CONTRIBUTION_COLUMNS = """
            rc.id AS contribution_id,
            relation.id AS relation_id,
            relation.source_entity_id,
            relation.target_entity_id,
            relation.relation_type,
            relation.orientation,
            rc.keywords,
            rc.description,
            rc.organization_id,
            rc.knowledge_asset_id,
            rc.source_revision_id,
            rc.chunk_id,
            rc.acl_snapshot_id,
            rc.acl_generation,
            rc.projection_generation,
            rc.extractor_provider,
            rc.extractor_model,
            rc.prompt_version,
            rc.confidence,
            rc.extracted_at
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ApacheAgeGraphTopologyProjection ageTopology;
    private final PostgresGraphStoreOptions options;

    public PostgresGraphProjectionStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this(jdbc, transactionManager, Clock.systemUTC(), PostgresGraphStoreOptions.defaults());
    }

    PostgresGraphProjectionStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(jdbc, transactionManager, clock, PostgresGraphStoreOptions.defaults());
    }

    public PostgresGraphProjectionStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            ApacheAgeMode ageMode) {
        this(
                jdbc,
                transactionManager,
                clock,
                PostgresGraphStoreOptions.defaults().withApacheAgeMode(ageMode));
    }

    public PostgresGraphProjectionStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            PostgresGraphStoreOptions options) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions =
                new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.options = Objects.requireNonNull(options, "options");
        this.ageTopology = new ApacheAgeGraphTopologyProjection(
                jdbc, options.apacheAgeMode());
        new PostgresGraphVectorIndexManager(jdbc.getJdbcTemplate())
                .ensureConfiguredIndexes(options);
    }

    @Override
    public void replaceRevision(GraphRevisionContributions contributions) {
        Objects.requireNonNull(contributions, "contributions");
        transactions.executeWithoutResult(status -> replaceRevisionInTransaction(contributions));
    }

    @Override
    public void removeRevision(UUID organizationId, UUID sourceRevisionId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        transactions.executeWithoutResult(status -> {
            lockRevision(organizationId, sourceRevisionId);
            MapSqlParameterSource parameters = revisionParameters(organizationId, sourceRevisionId);
            jdbc.update("""
                    DELETE FROM graph_projection_heads
                    WHERE organization_id = :organizationId
                      AND source_revision_id = :sourceRevisionId
                    """, parameters);
            removeOrphanIdentities(organizationId);
            ageTopology.removeRevision(organizationId, sourceRevisionId);
        });
    }

    @Override
    public void replaceRevisionEmbeddings(GraphRevisionEmbeddings embeddings) {
        Objects.requireNonNull(embeddings, "embeddings");
        transactions.executeWithoutResult(status -> replaceEmbeddingsInTransaction(embeddings));
    }

    @Override
    public List<EntityContribution> loadEntityContributions(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds) {
        Set<UUID> requestedIds = requestedIds(entityIds, "entityIds");
        if (scopeHasNoEvidence(scope) || requestedIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("entityIds", requestedIds);
        return jdbc.query("""
                WITH %s
                SELECT %s
                FROM visible_entity_contributions ec
                JOIN graph_entities entity
                  ON entity.organization_id = ec.organization_id
                 AND entity.id = ec.entity_id
                WHERE ec.entity_id IN (:entityIds)
                ORDER BY ec.id
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, ENTITY_CONTRIBUTION_COLUMNS),
                parameters,
                (resultSet, rowNumber) -> mapEntityContribution(resultSet));
    }

    @Override
    public List<RelationContribution> loadRelationContributions(
            AuthorizedGraphScope scope,
            Collection<UUID> relationIds) {
        Set<UUID> requestedIds = requestedIds(relationIds, "relationIds");
        if (scopeHasNoEvidence(scope) || requestedIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("relationIds", requestedIds);
        return jdbc.query("""
                WITH %s,
                     %s
                SELECT %s
                FROM visible_relation_contributions rc
                JOIN graph_relations relation
                  ON relation.organization_id = rc.organization_id
                 AND relation.id = rc.relation_id
                WHERE rc.relation_id IN (:relationIds)
                ORDER BY rc.id
                """.formatted(
                        VISIBLE_ENTITY_CONTRIBUTIONS,
                        VISIBLE_RELATION_CONTRIBUTIONS,
                        RELATION_CONTRIBUTION_COLUMNS),
                parameters,
                (resultSet, rowNumber) -> mapRelationContribution(resultSet));
    }

    @Override
    public List<CanonicalRelation> loadIncidentRelations(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds,
            int limit) {
        Set<UUID> requestedIds = requestedIds(entityIds, "entityIds");
        requireNonNegative(limit, "limit");
        if (scopeHasNoEvidence(scope) || requestedIds.isEmpty() || limit == 0) {
            return List.of();
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("entityIds", requestedIds)
                .addValue("limit", limit);
        return jdbc.query("""
                WITH %s,
                     %s
                SELECT DISTINCT
                       relation.id AS relation_id,
                       relation.source_entity_id,
                       relation.target_entity_id,
                       relation.relation_type,
                       relation.orientation
                FROM visible_relation_contributions rc
                JOIN graph_relations relation
                  ON relation.organization_id = rc.organization_id
                 AND relation.id = rc.relation_id
                WHERE relation.source_entity_id IN (:entityIds)
                   OR relation.target_entity_id IN (:entityIds)
                ORDER BY relation.id
                LIMIT :limit
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, VISIBLE_RELATION_CONTRIBUTIONS),
                parameters,
                (resultSet, rowNumber) -> mapCanonicalRelation(resultSet));
    }

    @Override
    public Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds) {
        Set<UUID> requestedIds = requestedIds(entityIds, "entityIds");
        Map<UUID, Long> degrees = requestedIds.stream()
                .sorted()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ignored -> 0L,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (scopeHasNoEvidence(scope) || requestedIds.isEmpty()) {
            return Map.copyOf(degrees);
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("entityIds", requestedIds);
        jdbc.query("""
                WITH %s,
                     %s,
                     visible_relations AS (
                         SELECT DISTINCT
                                relation.id,
                                relation.source_entity_id,
                                relation.target_entity_id
                         FROM visible_relation_contributions rc
                         JOIN graph_relations relation
                           ON relation.organization_id = rc.organization_id
                          AND relation.id = rc.relation_id
                     ),
                     visible_endpoints AS (
                         SELECT source_entity_id AS entity_id, id AS relation_id
                         FROM visible_relations
                         UNION ALL
                         SELECT target_entity_id AS entity_id, id AS relation_id
                         FROM visible_relations
                     )
                SELECT entity_id, count(DISTINCT relation_id) AS degree
                FROM visible_endpoints
                WHERE entity_id IN (:entityIds)
                GROUP BY entity_id
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, VISIBLE_RELATION_CONTRIBUTIONS),
                parameters,
                (RowCallbackHandler) resultSet -> degrees.put(
                        resultSet.getObject("entity_id", UUID.class),
                        resultSet.getLong("degree")));
        return Map.copyOf(degrees);
    }

    @Override
    public Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedGraphScope scope,
            Collection<UUID> relationIds) {
        Set<UUID> requestedIds = requestedIds(relationIds, "relationIds");
        Map<UUID, Double> weights = requestedIds.stream()
                .sorted()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ignored -> 0.0,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (scopeHasNoEvidence(scope) || requestedIds.isEmpty()) {
            return Map.copyOf(weights);
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("relationIds", requestedIds);
        jdbc.query("""
                WITH %s,
                     %s
                SELECT relation_id, sum(confidence) AS weight
                FROM visible_relation_contributions
                WHERE relation_id IN (:relationIds)
                GROUP BY relation_id
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, VISIBLE_RELATION_CONTRIBUTIONS),
                parameters,
                (RowCallbackHandler) resultSet -> weights.put(
                        resultSet.getObject("relation_id", UUID.class),
                        resultSet.getDouble("weight")));
        return Map.copyOf(weights);
    }

    @Override
    public List<RankedItem<CanonicalEntity>> searchEntities(
            AuthorizedGraphScope scope,
            String query,
            int limit) {
        String normalizedQuery = requireQuery(query);
        requireNonNegative(limit, "limit");
        if (scopeHasNoEvidence(scope) || limit == 0) {
            return List.of();
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("query", normalizedQuery)
                .addValue("limit", limit);
        return jdbc.query("""
                WITH search_query AS (
                         SELECT websearch_to_tsquery('simple', :query) AS value
                     ),
                     %s,
                     scored_entities AS (
                         SELECT entity.id,
                                entity.normalized_name,
                                entity.entity_type,
                                max(
                                    greatest(
                                        ts_rank(entity.search_vector, search_query.value),
                                        ts_rank(ec.search_vector, search_query.value)
                                    )
                                    + CASE
                                        WHEN lower(entity.normalized_name) = lower(:query) THEN 3.0
                                        WHEN lower(entity.normalized_name) LIKE
                                             '%%' || lower(:query) || '%%' THEN 2.0
                                        ELSE 0.0
                                      END
                                ) AS score
                         FROM visible_entity_contributions ec
                         JOIN graph_entities entity
                           ON entity.organization_id = ec.organization_id
                          AND entity.id = ec.entity_id
                         CROSS JOIN search_query
                         WHERE entity.search_vector @@ search_query.value
                            OR ec.search_vector @@ search_query.value
                            OR lower(entity.normalized_name) LIKE
                               '%%' || lower(:query) || '%%'
                         GROUP BY entity.id, entity.normalized_name, entity.entity_type
                     )
                SELECT id, normalized_name, entity_type, score
                FROM scored_entities
                ORDER BY score DESC, id
                LIMIT :limit
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS),
                parameters,
                (resultSet, rowNumber) -> {
                    CanonicalEntity entity = new CanonicalEntity(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("normalized_name"),
                            resultSet.getString("entity_type"));
                    return new RankedItem<>(
                            entity.id().toString(),
                            entity,
                            resultSet.getDouble("score"));
                });
    }

    @Override
    public List<RankedItem<CanonicalRelation>> searchRelations(
            AuthorizedGraphScope scope,
            String query,
            int limit) {
        String normalizedQuery = requireQuery(query);
        requireNonNegative(limit, "limit");
        if (scopeHasNoEvidence(scope) || limit == 0) {
            return List.of();
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("query", normalizedQuery)
                .addValue("limit", limit);
        return jdbc.query("""
                WITH search_query AS (
                         SELECT websearch_to_tsquery('simple', :query) AS value
                     ),
                     %s,
                     %s,
                     scored_relations AS (
                         SELECT relation.id,
                                relation.source_entity_id,
                                relation.target_entity_id,
                                relation.relation_type,
                                relation.orientation,
                                max(
                                    greatest(
                                        ts_rank(relation.search_vector, search_query.value),
                                        ts_rank(rc.search_vector, search_query.value)
                                    )
                                    + CASE
                                        WHEN lower(relation.relation_type) = lower(:query) THEN 2.0
                                        WHEN lower(relation.relation_type) LIKE
                                             '%%' || lower(:query) || '%%' THEN 1.0
                                        ELSE 0.0
                                      END
                                ) AS score
                         FROM visible_relation_contributions rc
                         JOIN graph_relations relation
                           ON relation.organization_id = rc.organization_id
                          AND relation.id = rc.relation_id
                         CROSS JOIN search_query
                         WHERE relation.search_vector @@ search_query.value
                            OR rc.search_vector @@ search_query.value
                            OR lower(relation.relation_type) LIKE
                               '%%' || lower(:query) || '%%'
                         GROUP BY
                             relation.id,
                             relation.source_entity_id,
                             relation.target_entity_id,
                             relation.relation_type,
                             relation.orientation
                     )
                SELECT
                    id AS relation_id,
                    source_entity_id,
                    target_entity_id,
                    relation_type,
                    orientation,
                    score
                FROM scored_relations
                ORDER BY score DESC, id
                LIMIT :limit
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, VISIBLE_RELATION_CONTRIBUTIONS),
                parameters,
                (resultSet, rowNumber) -> {
                    CanonicalRelation relation = mapCanonicalRelation(resultSet);
                    return new RankedItem<>(
                            relation.id().toString(),
                            relation,
                            resultSet.getDouble("score"));
                });
    }

    @Override
    public List<RankedItem<CanonicalEntity>> searchEntitiesByVector(
            AuthorizedGraphScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            List<Float> queryEmbedding,
            double maximumCosineDistance,
            int limit) {
        VectorQuery vectorQuery = vectorQuery(
                scope,
                embeddingProfileId,
                embeddingDimensions,
                queryEmbedding,
                maximumCosineDistance,
                limit);
        if (vectorQuery.empty()) {
            return List.of();
        }
        String vectorExpression = vectorExpression(
                "embedding.content_vector", embeddingDimensions);
        String queryCast = vectorCast(":queryEmbedding", embeddingDimensions);
        String distanceExpression = vectorExpression + " <=> " + queryCast;
        return jdbc.query("""
                WITH visible_embeddings AS (
                    SELECT
                        entity.id,
                        entity.normalized_name,
                        entity.entity_type,
                        %s AS distance
                    FROM graph_entity_embeddings embedding
                    JOIN graph_entity_contributions contribution
                      ON contribution.organization_id = embedding.organization_id
                     AND contribution.id = embedding.entity_contribution_id
                    JOIN graph_projection_heads head
                      ON head.organization_id = contribution.organization_id
                     AND head.source_revision_id = contribution.source_revision_id
                     AND head.knowledge_asset_id = contribution.knowledge_asset_id
                     AND head.projection_generation = contribution.projection_generation
                    JOIN graph_entities entity
                      ON entity.organization_id = contribution.organization_id
                     AND entity.id = contribution.entity_id
                    WHERE embedding.organization_id = :organizationId
                      AND embedding.knowledge_asset_id IN (:authorizedAssetIds)
                      AND embedding.embedding_profile_id = :embeddingProfileId
                      AND embedding.embedding_dimensions = :embeddingDimensions
                ),
                scored_entities AS (
                    SELECT
                        id,
                        normalized_name,
                        entity_type,
                        min(distance) AS distance
                    FROM visible_embeddings
                    WHERE distance <= :maximumCosineDistance
                    GROUP BY id, normalized_name, entity_type
                )
                SELECT id, normalized_name, entity_type, 1.0 - distance AS score
                FROM scored_entities
                ORDER BY distance, id
                LIMIT :limit
                """.formatted(distanceExpression),
                vectorQuery.parameters(),
                (resultSet, rowNumber) -> {
                    CanonicalEntity entity = new CanonicalEntity(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("normalized_name"),
                            resultSet.getString("entity_type"));
                    return new RankedItem<>(
                            entity.id().toString(),
                            entity,
                            resultSet.getDouble("score"));
                });
    }

    @Override
    public List<RankedItem<CanonicalRelation>> searchRelationsByVector(
            AuthorizedGraphScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            List<Float> queryEmbedding,
            double maximumCosineDistance,
            int limit) {
        VectorQuery vectorQuery = vectorQuery(
                scope,
                embeddingProfileId,
                embeddingDimensions,
                queryEmbedding,
                maximumCosineDistance,
                limit);
        if (vectorQuery.empty()) {
            return List.of();
        }
        String vectorExpression = vectorExpression(
                "embedding.content_vector", embeddingDimensions);
        String queryCast = vectorCast(":queryEmbedding", embeddingDimensions);
        String distanceExpression = vectorExpression + " <=> " + queryCast;
        return jdbc.query("""
                WITH %s,
                     visible_embeddings AS (
                         SELECT
                             relation.id,
                             relation.source_entity_id,
                             relation.target_entity_id,
                             relation.relation_type,
                             relation.orientation,
                             %s AS distance
                         FROM graph_relation_embeddings embedding
                         JOIN graph_relation_contributions contribution
                           ON contribution.organization_id = embedding.organization_id
                          AND contribution.id = embedding.relation_contribution_id
                         JOIN graph_projection_heads head
                           ON head.organization_id = contribution.organization_id
                          AND head.source_revision_id = contribution.source_revision_id
                          AND head.knowledge_asset_id = contribution.knowledge_asset_id
                          AND head.projection_generation = contribution.projection_generation
                         JOIN graph_relations relation
                           ON relation.organization_id = contribution.organization_id
                          AND relation.id = contribution.relation_id
                         WHERE embedding.organization_id = :organizationId
                           AND embedding.knowledge_asset_id IN (:authorizedAssetIds)
                           AND embedding.embedding_profile_id = :embeddingProfileId
                           AND embedding.embedding_dimensions = :embeddingDimensions
                           AND EXISTS (
                               SELECT 1
                               FROM visible_entity_contributions source_evidence
                               WHERE source_evidence.entity_id = relation.source_entity_id
                           )
                           AND EXISTS (
                               SELECT 1
                               FROM visible_entity_contributions target_evidence
                               WHERE target_evidence.entity_id = relation.target_entity_id
                           )
                     ),
                     scored_relations AS (
                         SELECT
                             id,
                             source_entity_id,
                             target_entity_id,
                             relation_type,
                             orientation,
                             min(distance) AS distance
                         FROM visible_embeddings
                         WHERE distance <= :maximumCosineDistance
                         GROUP BY
                             id,
                             source_entity_id,
                             target_entity_id,
                             relation_type,
                             orientation
                     )
                SELECT
                    id AS relation_id,
                    source_entity_id,
                    target_entity_id,
                    relation_type,
                    orientation,
                    1.0 - distance AS score
                FROM scored_relations
                ORDER BY distance, id
                LIMIT :limit
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, distanceExpression),
                vectorQuery.parameters(),
                (resultSet, rowNumber) -> {
                    CanonicalRelation relation = mapCanonicalRelation(resultSet);
                    return new RankedItem<>(
                            relation.id().toString(),
                            relation,
                            resultSet.getDouble("score"));
                });
    }

    @Override
    public List<UUID> expandEntityIds(
            AuthorizedGraphScope scope,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        Set<UUID> requestedSeeds = requestedIds(seedEntityIds, "seedEntityIds");
        requireNonNegative(limit, "limit");
        if (maximumDepth < 1 || maximumDepth > 5) {
            throw new IllegalArgumentException("maximumDepth must be between 1 and 5");
        }
        if (scopeHasNoEvidence(scope) || requestedSeeds.isEmpty() || limit == 0) {
            return List.of();
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("seedEntityIds", requestedSeeds)
                .addValue("maximumDepth", maximumDepth)
                .addValue("limit", limit);
        Set<UUID> visibleSeeds = Set.copyOf(jdbc.queryForList("""
                WITH %s
                SELECT DISTINCT entity_id
                FROM visible_entity_contributions
                WHERE entity_id IN (:seedEntityIds)
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS), parameters, UUID.class));
        if (visibleSeeds.isEmpty()) {
            return List.of();
        }
        if (ageTopology.isAvailable()) {
            return transactions.execute(status -> ageTopology.expandEntityIds(
                    scope.organizationId(),
                    visibleSeeds,
                    scope.authorizedAssetIds(),
                    maximumDepth,
                    limit));
        }
        return expandEntityIdsRelationally(scope, visibleSeeds, maximumDepth, limit);
    }

    private List<UUID> expandEntityIdsRelationally(
            AuthorizedGraphScope scope,
            Set<UUID> visibleSeeds,
            int maximumDepth,
            int limit) {
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("seedEntityIds", visibleSeeds)
                .addValue("maximumDepth", maximumDepth)
                .addValue("limit", limit);
        return jdbc.queryForList("""
                WITH RECURSIVE
                     %s,
                     %s,
                     visible_edges AS (
                         SELECT DISTINCT
                             relation.source_entity_id,
                             relation.target_entity_id
                         FROM visible_relation_contributions contribution
                         JOIN graph_relations relation
                           ON relation.organization_id = contribution.organization_id
                          AND relation.id = contribution.relation_id
                     ),
                     walk(entity_id, depth, visited) AS (
                         SELECT seed_id, 0, ARRAY[seed_id]::uuid[]
                         FROM unnest(ARRAY[:seedEntityIds]::uuid[]) seed_id
                         UNION ALL
                         SELECT
                             CASE
                                 WHEN edge.source_entity_id = walk.entity_id
                                     THEN edge.target_entity_id
                                 ELSE edge.source_entity_id
                             END,
                             walk.depth + 1,
                             walk.visited || CASE
                                 WHEN edge.source_entity_id = walk.entity_id
                                     THEN edge.target_entity_id
                                 ELSE edge.source_entity_id
                             END
                         FROM walk
                         JOIN visible_edges edge
                           ON edge.source_entity_id = walk.entity_id
                           OR edge.target_entity_id = walk.entity_id
                         WHERE walk.depth < :maximumDepth
                           AND NOT (
                               CASE
                                   WHEN edge.source_entity_id = walk.entity_id
                                       THEN edge.target_entity_id
                                   ELSE edge.source_entity_id
                               END = ANY(walk.visited)
                           )
                     )
                SELECT DISTINCT entity_id
                FROM walk
                WHERE depth > 0
                  AND entity_id NOT IN (:seedEntityIds)
                ORDER BY entity_id
                LIMIT :limit
                """.formatted(VISIBLE_ENTITY_CONTRIBUTIONS, VISIBLE_RELATION_CONTRIBUTIONS),
                parameters,
                UUID.class);
    }

    private void replaceRevisionInTransaction(GraphRevisionContributions contributions) {
        lockRevision(contributions.organizationId(), contributions.sourceRevisionId());
        ProjectionHead existingHead = findHead(contributions.organizationId(), contributions.sourceRevisionId());
        if (existingHead != null
                && contributions.projectionGeneration() < existingHead.projectionGeneration()) {
            throw new IllegalArgumentException(
                    "projection generation cannot move backwards from "
                            + existingHead.projectionGeneration()
                            + " to "
                            + contributions.projectionGeneration());
        }
        if (existingHead != null
                && !existingHead.knowledgeAssetId().equals(contributions.knowledgeAssetId())) {
            throw new IllegalArgumentException(
                    "a source revision cannot change its knowledge asset");
        }
        rejectContributionIdCollisions(contributions);
        verifyCanonicalIdentities(contributions);
        insertCanonicalEntities(contributions);
        insertCanonicalRelations(contributions);

        MapSqlParameterSource revision = revisionParameters(
                contributions.organizationId(), contributions.sourceRevisionId());
        jdbc.update("""
                DELETE FROM graph_relation_contributions
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                """, revision);
        jdbc.update("""
                DELETE FROM graph_entity_contributions
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                """, revision);

        MapSqlParameterSource head = new MapSqlParameterSource()
                .addValue("organizationId", contributions.organizationId())
                .addValue("sourceRevisionId", contributions.sourceRevisionId())
                .addValue("knowledgeAssetId", contributions.knowledgeAssetId())
                .addValue("projectionGeneration", contributions.projectionGeneration())
                .addValue("publishedAt", Timestamp.from(clock.instant()));
        jdbc.update("""
                INSERT INTO graph_projection_heads (
                    organization_id,
                    source_revision_id,
                    knowledge_asset_id,
                    projection_generation,
                    published_at
                )
                VALUES (
                    :organizationId,
                    :sourceRevisionId,
                    :knowledgeAssetId,
                    :projectionGeneration,
                    :publishedAt
                )
                ON CONFLICT (organization_id, source_revision_id)
                DO UPDATE SET
                    knowledge_asset_id = excluded.knowledge_asset_id,
                    projection_generation = excluded.projection_generation,
                    published_at = excluded.published_at
                """, head);

        insertEntityContributions(contributions.entities());
        insertRelationContributions(contributions.relations());
        removeOrphanIdentities(contributions.organizationId());
        ageTopology.replaceRevision(contributions);
    }

    private void replaceEmbeddingsInTransaction(GraphRevisionEmbeddings embeddings) {
        lockRevision(embeddings.organizationId(), embeddings.sourceRevisionId());
        ProjectionHead head = findHead(embeddings.organizationId(), embeddings.sourceRevisionId());
        if (head == null
                || !head.knowledgeAssetId().equals(embeddings.knowledgeAssetId())
                || head.projectionGeneration() != embeddings.projectionGeneration()) {
            throw new IllegalArgumentException(
                    "embeddings must target the current published graph generation");
        }
        verifyEmbeddingContributionIds(embeddings);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizationId", embeddings.organizationId())
                .addValue("sourceRevisionId", embeddings.sourceRevisionId())
                .addValue("embeddingProfileId", embeddings.embeddingProfileId());
        jdbc.update("""
                DELETE FROM graph_entity_embeddings
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                  AND embedding_profile_id = :embeddingProfileId
                """, parameters);
        jdbc.update("""
                DELETE FROM graph_relation_embeddings
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                  AND embedding_profile_id = :embeddingProfileId
                """, parameters);
        insertEntityEmbeddings(embeddings);
        insertRelationEmbeddings(embeddings);
    }

    private void verifyEmbeddingContributionIds(GraphRevisionEmbeddings embeddings) {
        verifyEmbeddingContributionIds(
                "graph_entity_contributions",
                embeddings,
                embeddings.entityEmbeddings());
        verifyEmbeddingContributionIds(
                "graph_relation_contributions",
                embeddings,
                embeddings.relationEmbeddings());
    }

    private void verifyEmbeddingContributionIds(
            String tableName,
            GraphRevisionEmbeddings embeddings,
            List<ContributionEmbedding> contributionEmbeddings) {
        if (contributionEmbeddings.isEmpty()) {
            return;
        }
        Set<UUID> expectedIds = contributionEmbeddings.stream()
                .map(ContributionEmbedding::contributionId)
                .collect(Collectors.toSet());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizationId", embeddings.organizationId())
                .addValue("sourceRevisionId", embeddings.sourceRevisionId())
                .addValue("knowledgeAssetId", embeddings.knowledgeAssetId())
                .addValue("projectionGeneration", embeddings.projectionGeneration())
                .addValue("contributionIds", expectedIds);
        List<UUID> persistedIds = jdbc.queryForList("""
                SELECT id
                FROM %s
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                  AND knowledge_asset_id = :knowledgeAssetId
                  AND projection_generation = :projectionGeneration
                  AND id IN (:contributionIds)
                """.formatted(tableName), parameters, UUID.class);
        if (!Set.copyOf(persistedIds).equals(expectedIds)) {
            throw new IllegalArgumentException(
                    "embedding contribution ids must belong to the target revision generation");
        }
    }

    private void insertEntityEmbeddings(GraphRevisionEmbeddings embeddings) {
        insertEmbeddings(
                "graph_entity_embeddings",
                "entity_contribution_id",
                embeddings,
                embeddings.entityEmbeddings());
    }

    private void insertRelationEmbeddings(GraphRevisionEmbeddings embeddings) {
        insertEmbeddings(
                "graph_relation_embeddings",
                "relation_contribution_id",
                embeddings,
                embeddings.relationEmbeddings());
    }

    private void insertEmbeddings(
            String tableName,
            String contributionColumn,
            GraphRevisionEmbeddings embeddings,
            List<ContributionEmbedding> contributionEmbeddings) {
        if (contributionEmbeddings.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO %s (
                    organization_id,
                    %s,
                    source_revision_id,
                    knowledge_asset_id,
                    projection_generation,
                    embedding_profile_id,
                    embedding_dimensions,
                    content_vector,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, ?)
                """.formatted(tableName, contributionColumn);
        BoundedBatcher.forEachBatch(
                contributionEmbeddings,
                options.maxBatchRecords(),
                options.maxBatchPayloadBytes(),
                embedding -> 256L + (long) embedding.vector().size() * Float.BYTES,
                batch -> insertEmbeddingBatch(sql, embeddings, batch));
    }

    private void insertEmbeddingBatch(
            String sql,
            GraphRevisionEmbeddings embeddings,
            List<ContributionEmbedding> batch) {
        jdbc.getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement statement, int index)
                    throws SQLException {
                ContributionEmbedding embedding = batch.get(index);
                statement.setObject(1, embeddings.organizationId(), Types.OTHER);
                statement.setObject(2, embedding.contributionId(), Types.OTHER);
                statement.setObject(3, embeddings.sourceRevisionId(), Types.OTHER);
                statement.setObject(4, embeddings.knowledgeAssetId(), Types.OTHER);
                statement.setLong(5, embeddings.projectionGeneration());
                statement.setObject(6, embeddings.embeddingProfileId(), Types.OTHER);
                statement.setInt(7, embeddings.embeddingDimensions());
                statement.setString(8, vectorLiteral(embedding.vector()));
                statement.setTimestamp(9, Timestamp.from(clock.instant()));
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }

    private void lockRevision(UUID organizationId, UUID sourceRevisionId) {
        jdbc.query("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended(
                        CAST(:organizationId AS text)
                        || ':'
                        || CAST(:sourceRevisionId AS text),
                        0
                    )
                )
                """,
                revisionParameters(organizationId, sourceRevisionId),
                (RowCallbackHandler) resultSet -> {
                    // pg_advisory_xact_lock returns void; consuming the row is sufficient.
                });
    }

    private ProjectionHead findHead(UUID organizationId, UUID sourceRevisionId) {
        List<ProjectionHead> heads = jdbc.query("""
                SELECT knowledge_asset_id, projection_generation
                FROM graph_projection_heads
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                FOR UPDATE
                """,
                revisionParameters(organizationId, sourceRevisionId),
                (resultSet, rowNumber) -> new ProjectionHead(
                        resultSet.getObject("knowledge_asset_id", UUID.class),
                        resultSet.getLong("projection_generation")));
        return heads.isEmpty() ? null : heads.getFirst();
    }

    private void rejectContributionIdCollisions(GraphRevisionContributions contributions) {
        List<UUID> contributionIds = new ArrayList<>();
        contributions.entities().stream().map(EntityContribution::id).forEach(contributionIds::add);
        contributions.relations().stream().map(RelationContribution::id).forEach(contributionIds::add);
        if (contributionIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource parameters = revisionParameters(
                        contributions.organizationId(), contributions.sourceRevisionId())
                .addValue("contributionIds", contributionIds);
        Boolean collision = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM graph_entity_contributions
                    WHERE organization_id = :organizationId
                      AND id IN (:contributionIds)
                      AND source_revision_id <> :sourceRevisionId
                    UNION ALL
                    SELECT 1
                    FROM graph_relation_contributions
                    WHERE organization_id = :organizationId
                      AND id IN (:contributionIds)
                      AND source_revision_id <> :sourceRevisionId
                )
                """, parameters, Boolean.class);
        if (Boolean.TRUE.equals(collision)) {
            throw new IllegalArgumentException(
                    "contribution id already belongs to another revision");
        }
    }

    private void verifyCanonicalIdentities(GraphRevisionContributions contributions) {
        Map<UUID, CanonicalEntity> expectedEntities = contributions.entities().stream()
                .map(EntityContribution::entity)
                .collect(Collectors.toMap(CanonicalEntity::id, Function.identity(), (left, right) -> {
                    if (!left.equals(right)) {
                        throw new IllegalArgumentException(
                                "entity id resolves to conflicting identities within the batch");
                    }
                    return left;
                }));
        if (!expectedEntities.isEmpty()) {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("organizationId", contributions.organizationId())
                    .addValue("entityIds", expectedEntities.keySet());
            jdbc.query("""
                    SELECT id, normalized_name, entity_type
                    FROM graph_entities
                    WHERE organization_id = :organizationId
                      AND id IN (:entityIds)
                    """,
                    parameters,
                    (RowCallbackHandler) resultSet -> {
                        CanonicalEntity actual = new CanonicalEntity(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("normalized_name"),
                                resultSet.getString("entity_type"));
                        if (!actual.equals(expectedEntities.get(actual.id()))) {
                            throw new IllegalArgumentException(
                                    "entity id already resolves to another canonical identity: "
                                            + actual.id());
                        }
                    });
        }

        Map<UUID, CanonicalRelation> expectedRelations = contributions.relations().stream()
                .map(RelationContribution::relation)
                .collect(Collectors.toMap(CanonicalRelation::id, Function.identity(), (left, right) -> {
                    if (!left.equals(right)) {
                        throw new IllegalArgumentException(
                                "relation id resolves to conflicting identities within the batch");
                    }
                    return left;
                }));
        if (!expectedRelations.isEmpty()) {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("organizationId", contributions.organizationId())
                    .addValue("relationIds", expectedRelations.keySet());
            jdbc.query("""
                    SELECT
                        id AS relation_id,
                        source_entity_id,
                        target_entity_id,
                        relation_type,
                        orientation
                    FROM graph_relations
                    WHERE organization_id = :organizationId
                      AND id IN (:relationIds)
                    """,
                    parameters,
                    (RowCallbackHandler) resultSet -> {
                        CanonicalRelation actual = mapCanonicalRelation(resultSet);
                        if (!actual.equals(expectedRelations.get(actual.id()))) {
                            throw new IllegalArgumentException(
                                    "relation id already resolves to another canonical identity: "
                                            + actual.id());
                        }
                    });
        }
    }

    private void insertCanonicalEntities(GraphRevisionContributions contributions) {
        List<CanonicalEntity> entities = contributions.entities().stream()
                .map(EntityContribution::entity)
                .distinct()
                .sorted(Comparator.comparing(CanonicalEntity::id))
                .toList();
        if (entities.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        BoundedBatcher.forEachBatch(
                entities,
                options.maxBatchRecords(),
                options.maxBatchPayloadBytes(),
                entity -> 128L + utf8Bytes(entity.normalizedName()) + utf8Bytes(entity.type()),
                batch -> {
                    MapSqlParameterSource[] parameters = batch.stream()
                            .map(entity -> new MapSqlParameterSource()
                                    .addValue("organizationId", contributions.organizationId())
                                    .addValue("id", entity.id())
                                    .addValue("normalizedName", entity.normalizedName())
                                    .addValue("entityType", entity.type())
                                    .addValue("now", Timestamp.from(now)))
                            .toArray(MapSqlParameterSource[]::new);
                    jdbc.batchUpdate("""
                            INSERT INTO graph_entities (
                                organization_id,
                                id,
                                normalized_name,
                                entity_type,
                                created_at,
                                updated_at
                            )
                            VALUES (
                                :organizationId,
                                :id,
                                :normalizedName,
                                :entityType,
                                :now,
                                :now
                            )
                            ON CONFLICT (organization_id, id) DO NOTHING
                            """, parameters);
                });
    }

    private void insertCanonicalRelations(GraphRevisionContributions contributions) {
        List<CanonicalRelation> relations = contributions.relations().stream()
                .map(RelationContribution::relation)
                .distinct()
                .sorted(Comparator.comparing(CanonicalRelation::id))
                .toList();
        if (relations.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        BoundedBatcher.forEachBatch(
                relations,
                options.maxBatchRecords(),
                options.maxBatchPayloadBytes(),
                relation -> 192L + utf8Bytes(relation.type()),
                batch -> {
                    MapSqlParameterSource[] parameters = batch.stream()
                            .map(relation -> new MapSqlParameterSource()
                                    .addValue("organizationId", contributions.organizationId())
                                    .addValue("id", relation.id())
                                    .addValue("sourceEntityId", relation.sourceEntityId())
                                    .addValue("targetEntityId", relation.targetEntityId())
                                    .addValue("relationType", relation.type())
                                    .addValue("orientation", relation.orientation().name())
                                    .addValue("now", Timestamp.from(now)))
                            .toArray(MapSqlParameterSource[]::new);
                    jdbc.batchUpdate("""
                            INSERT INTO graph_relations (
                                organization_id,
                                id,
                                source_entity_id,
                                target_entity_id,
                                relation_type,
                                orientation,
                                created_at,
                                updated_at
                            )
                            VALUES (
                                :organizationId,
                                :id,
                                :sourceEntityId,
                                :targetEntityId,
                                :relationType,
                                :orientation,
                                :now,
                                :now
                            )
                            ON CONFLICT (organization_id, id) DO NOTHING
                            """, parameters);
                });
    }

    private void insertEntityContributions(List<EntityContribution> contributions) {
        if (contributions.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO graph_entity_contributions (
                    organization_id,
                    id,
                    entity_id,
                    knowledge_asset_id,
                    source_revision_id,
                    chunk_id,
                    acl_snapshot_id,
                    acl_generation,
                    projection_generation,
                    description,
                    extractor_provider,
                    extractor_model,
                    prompt_version,
                    confidence,
                    extracted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        BoundedBatcher.forEachBatch(
                contributions,
                options.maxBatchRecords(),
                options.maxBatchPayloadBytes(),
                contribution -> 320L
                        + utf8Bytes(contribution.entity().normalizedName())
                        + utf8Bytes(contribution.entity().type())
                        + utf8Bytes(contribution.description()),
                batch -> insertEntityContributionBatch(sql, batch));
    }

    private void insertEntityContributionBatch(
            String sql, List<EntityContribution> contributions) {
        jdbc.getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement statement, int index)
                    throws SQLException {
                EntityContribution contribution = contributions.get(index);
                EvidenceProvenance provenance = contribution.provenance();
                statement.setObject(1, provenance.organizationId(), Types.OTHER);
                statement.setObject(2, contribution.id(), Types.OTHER);
                statement.setObject(3, contribution.entity().id(), Types.OTHER);
                setProvenance(statement, 4, provenance);
                statement.setString(10, contribution.description());
                setExtractor(statement, 11, provenance);
            }

            @Override
            public int getBatchSize() {
                return contributions.size();
            }
        });
    }

    private void insertRelationContributions(List<RelationContribution> contributions) {
        if (contributions.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO graph_relation_contributions (
                    organization_id,
                    id,
                    relation_id,
                    knowledge_asset_id,
                    source_revision_id,
                    chunk_id,
                    acl_snapshot_id,
                    acl_generation,
                    projection_generation,
                    keywords,
                    description,
                    search_content,
                    extractor_provider,
                    extractor_model,
                    prompt_version,
                    confidence,
                    extracted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        BoundedBatcher.forEachBatch(
                contributions,
                options.maxBatchRecords(),
                options.maxBatchPayloadBytes(),
                contribution -> 448L
                        + utf8Bytes(contribution.relation().type())
                        + utf8Bytes(contribution.description())
                        + contribution.keywords().stream()
                                .mapToLong(PostgresGraphProjectionStore::utf8Bytes)
                                .sum(),
                batch -> insertRelationContributionBatch(sql, batch));
    }

    private void insertRelationContributionBatch(
            String sql, List<RelationContribution> contributions) {
        jdbc.getJdbcTemplate().batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement statement, int index)
                    throws SQLException {
                RelationContribution contribution = contributions.get(index);
                EvidenceProvenance provenance = contribution.provenance();
                statement.setObject(1, provenance.organizationId(), Types.OTHER);
                statement.setObject(2, contribution.id(), Types.OTHER);
                statement.setObject(3, contribution.relation().id(), Types.OTHER);
                setProvenance(statement, 4, provenance);
                Array keywords = statement.getConnection()
                        .createArrayOf("varchar", contribution.keywords().toArray(String[]::new));
                statement.setArray(10, keywords);
                statement.setString(11, contribution.description());
                statement.setString(12, relationSearchContent(contribution));
                setExtractor(statement, 13, provenance);
            }

            @Override
            public int getBatchSize() {
                return contributions.size();
            }
        });
    }

    private static long utf8Bytes(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static void setProvenance(
            java.sql.PreparedStatement statement,
            int startIndex,
            EvidenceProvenance provenance)
            throws SQLException {
        statement.setObject(startIndex, provenance.knowledgeAssetId(), Types.OTHER);
        statement.setObject(startIndex + 1, provenance.sourceRevisionId(), Types.OTHER);
        statement.setObject(startIndex + 2, provenance.chunkId(), Types.OTHER);
        statement.setObject(startIndex + 3, provenance.aclSnapshotId(), Types.OTHER);
        statement.setLong(startIndex + 4, provenance.aclGeneration());
        statement.setLong(startIndex + 5, provenance.projectionGeneration());
    }

    private static void setExtractor(
            java.sql.PreparedStatement statement,
            int startIndex,
            EvidenceProvenance provenance)
            throws SQLException {
        statement.setString(startIndex, provenance.extractorProvider());
        statement.setString(startIndex + 1, provenance.extractorModel());
        statement.setString(startIndex + 2, provenance.promptVersion());
        statement.setDouble(startIndex + 3, provenance.confidence());
        statement.setTimestamp(startIndex + 4, Timestamp.from(provenance.extractedAt()));
    }

    private void removeOrphanIdentities(UUID organizationId) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource("organizationId", organizationId);
        jdbc.update("""
                DELETE FROM graph_relations relation
                WHERE relation.organization_id = :organizationId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM graph_relation_contributions contribution
                      WHERE contribution.organization_id = relation.organization_id
                        AND contribution.relation_id = relation.id
                  )
                """, parameters);
        jdbc.update("""
                DELETE FROM graph_entities entity
                WHERE entity.organization_id = :organizationId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM graph_entity_contributions contribution
                      WHERE contribution.organization_id = entity.organization_id
                        AND contribution.entity_id = entity.id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM graph_relations relation
                      WHERE relation.organization_id = entity.organization_id
                        AND (
                            relation.source_entity_id = entity.id
                            OR relation.target_entity_id = entity.id
                        )
                  )
                """, parameters);
    }

    private static EntityContribution mapEntityContribution(ResultSet resultSet)
            throws SQLException {
        CanonicalEntity entity = new CanonicalEntity(
                resultSet.getObject("entity_id", UUID.class),
                resultSet.getString("normalized_name"),
                resultSet.getString("entity_type"));
        return new EntityContribution(
                resultSet.getObject("contribution_id", UUID.class),
                entity,
                resultSet.getString("description"),
                mapProvenance(resultSet));
    }

    private static RelationContribution mapRelationContribution(ResultSet resultSet)
            throws SQLException {
        return new RelationContribution(
                resultSet.getObject("contribution_id", UUID.class),
                mapCanonicalRelation(resultSet),
                readKeywords(resultSet.getArray("keywords")),
                resultSet.getString("description"),
                mapProvenance(resultSet));
    }

    private static CanonicalRelation mapCanonicalRelation(ResultSet resultSet)
            throws SQLException {
        return new CanonicalRelation(
                resultSet.getObject("relation_id", UUID.class),
                resultSet.getObject("source_entity_id", UUID.class),
                resultSet.getObject("target_entity_id", UUID.class),
                resultSet.getString("relation_type"),
                RelationOrientation.valueOf(resultSet.getString("orientation")));
    }

    private static List<String> readKeywords(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object value = sqlArray.getArray();
            if (value instanceof String[] keywords) {
                return List.of(keywords);
            }
            Object[] values = (Object[]) value;
            return java.util.Arrays.stream(values).map(String::valueOf).toList();
        } finally {
            sqlArray.free();
        }
    }

    private static EvidenceProvenance mapProvenance(ResultSet resultSet)
            throws SQLException {
        return new EvidenceProvenance(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject("knowledge_asset_id", UUID.class),
                resultSet.getObject("source_revision_id", UUID.class),
                resultSet.getObject("chunk_id", UUID.class),
                resultSet.getObject("acl_snapshot_id", UUID.class),
                resultSet.getLong("acl_generation"),
                resultSet.getLong("projection_generation"),
                resultSet.getString("extractor_provider"),
                resultSet.getString("extractor_model"),
                resultSet.getString("prompt_version"),
                resultSet.getDouble("confidence"),
                resultSet.getTimestamp("extracted_at").toInstant());
    }

    private static MapSqlParameterSource scopeParameters(AuthorizedGraphScope scope) {
        Objects.requireNonNull(scope, "scope");
        return new MapSqlParameterSource()
                .addValue("organizationId", scope.organizationId())
                .addValue("authorizedAssetIds", scope.authorizedAssetIds());
    }

    private static MapSqlParameterSource revisionParameters(
            UUID organizationId,
            UUID sourceRevisionId) {
        return new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("sourceRevisionId", sourceRevisionId);
    }

    private static boolean scopeHasNoEvidence(AuthorizedGraphScope scope) {
        Objects.requireNonNull(scope, "scope");
        return scope.authorizedAssetIds().isEmpty();
    }

    private static Set<UUID> requestedIds(Collection<UUID> ids, String field) {
        Objects.requireNonNull(ids, field);
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null values");
        }
        return Set.copyOf(ids);
    }

    private static String requireQuery(String query) {
        Objects.requireNonNull(query, "query");
        String normalized = query.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return normalized;
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static VectorQuery vectorQuery(
            AuthorizedGraphScope scope,
            UUID embeddingProfileId,
            int embeddingDimensions,
            List<Float> queryEmbedding,
            double maximumCosineDistance,
            int limit) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
        Objects.requireNonNull(queryEmbedding, "queryEmbedding");
        requireNonNegative(limit, "limit");
        if (embeddingDimensions <= 0 || embeddingDimensions > 16000) {
            throw new IllegalArgumentException(
                    "embeddingDimensions must be between 1 and 16000");
        }
        if (queryEmbedding.size() != embeddingDimensions
                || queryEmbedding.stream()
                        .anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException(
                    "queryEmbedding must contain exactly embeddingDimensions finite values");
        }
        if (!Double.isFinite(maximumCosineDistance)
                || maximumCosineDistance < 0.0
                || maximumCosineDistance > 2.0) {
            throw new IllegalArgumentException(
                    "maximumCosineDistance must be between 0 and 2");
        }
        MapSqlParameterSource parameters = scopeParameters(scope)
                .addValue("embeddingProfileId", embeddingProfileId)
                .addValue("embeddingDimensions", embeddingDimensions)
                .addValue("queryEmbedding", vectorLiteral(queryEmbedding))
                .addValue("maximumCosineDistance", maximumCosineDistance)
                .addValue("limit", limit);
        return new VectorQuery(
                scope.authorizedAssetIds().isEmpty() || limit == 0,
                parameters);
    }

    private static String vectorExpression(String column, int dimensions) {
        return "(" + column + "::vector(" + dimensions + "))";
    }

    private static String vectorCast(String parameter, int dimensions) {
        return "CAST(" + parameter + " AS vector(" + dimensions + "))";
    }

    private static String vectorLiteral(List<Float> vector) {
        return vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String relationSearchContent(RelationContribution contribution) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(contribution.description()),
                        contribution.keywords().stream())
                .collect(Collectors.joining(" "));
    }

    private record ProjectionHead(UUID knowledgeAssetId, long projectionGeneration) {
    }

    private record VectorQuery(boolean empty, MapSqlParameterSource parameters) {
    }
}
