package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public final class PostgresGraphStore implements GraphStore {

    private static final List<String> COPY_PREDECESSOR = List.of(
            """
            INSERT INTO projection_graph_entities (
                batch_id, entity_id, normalized_name)
            SELECT :batchId, entity_id, normalized_name
            FROM projection_graph_entities
            WHERE batch_id = :predecessorBatchId
            """,
            """
            INSERT INTO projection_graph_relations (
                batch_id, relation_id, source_entity_id, target_entity_id, orientation)
            SELECT
                :batchId, relation_id, source_entity_id, target_entity_id, orientation
            FROM projection_graph_relations
            WHERE batch_id = :predecessorBatchId
            """,
            """
            INSERT INTO projection_graph_entity_contributions (
                batch_id, contribution_id, entity_id, entity_type, description,
                organization_id, knowledge_asset_id, source_revision_id, chunk_id,
                acl_snapshot_id, acl_generation, projection_generation,
                extractor_provider, extractor_model, prompt_version,
                extraction_profile_fingerprint, confidence, extracted_at)
            SELECT
                :batchId, contribution_id, entity_id, entity_type, description,
                organization_id, knowledge_asset_id, source_revision_id, chunk_id,
                acl_snapshot_id, acl_generation, :generation,
                extractor_provider, extractor_model, prompt_version,
                extraction_profile_fingerprint, confidence, extracted_at
            FROM projection_graph_entity_contributions
            WHERE batch_id = :predecessorBatchId
            """,
            """
            INSERT INTO projection_graph_relation_contributions (
                batch_id, contribution_id, relation_id, relation_type, keywords,
                description, weight, organization_id, knowledge_asset_id,
                source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                projection_generation, extractor_provider, extractor_model,
                prompt_version, extraction_profile_fingerprint, confidence, extracted_at)
            SELECT
                :batchId, contribution_id, relation_id, relation_type, keywords,
                description, weight, organization_id, knowledge_asset_id,
                source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                :generation, extractor_provider, extractor_model,
                prompt_version, extraction_profile_fingerprint, confidence, extracted_at
            FROM projection_graph_relation_contributions
            WHERE batch_id = :predecessorBatchId
            """);

    private static final String VISIBLE_ENTITY_CONTRIBUTIONS = """
            SELECT contribution.*
            FROM projection_graph_entity_contributions contribution
            WHERE contribution.batch_id = :batchId
              AND contribution.organization_id = :organizationId
              AND contribution.knowledge_asset_id IN (:authorizedAssetIds)
              AND contribution.projection_generation = :generation
            """;

    private static final String VISIBLE_RELATION_CONTRIBUTIONS = """
            SELECT contribution.*
            FROM projection_graph_relation_contributions contribution
            WHERE contribution.batch_id = :batchId
              AND contribution.organization_id = :organizationId
              AND contribution.knowledge_asset_id IN (:authorizedAssetIds)
              AND contribution.projection_generation = :generation
              AND EXISTS (
                  SELECT 1
                  FROM projection_graph_relations relation
                  WHERE relation.batch_id = contribution.batch_id
                    AND relation.relation_id = contribution.relation_id
                    AND EXISTS (
                        SELECT 1
                        FROM projection_graph_entity_contributions source_contribution
                        WHERE source_contribution.batch_id = relation.batch_id
                          AND source_contribution.entity_id = relation.source_entity_id
                          AND source_contribution.organization_id = :organizationId
                          AND source_contribution.knowledge_asset_id
                              IN (:authorizedAssetIds)
                          AND source_contribution.projection_generation = :generation
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM projection_graph_entity_contributions target_contribution
                        WHERE target_contribution.batch_id = relation.batch_id
                          AND target_contribution.entity_id = relation.target_entity_id
                          AND target_contribution.organization_id = :organizationId
                          AND target_contribution.knowledge_asset_id
                              IN (:authorizedAssetIds)
                          AND target_contribution.projection_generation = :generation
                    )
              )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostgresProjectionSupport support;

    public PostgresGraphStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.support =
                new PostgresProjectionSupport(jdbc, transactionManager, publications);
    }

    @Override
    public void stageReplaceRevision(
            ProjectionBatch batch,
            GraphRevisionContributions contributions) {
        Objects.requireNonNull(contributions, "contributions");
        if (!batch.namespace()
                        .organizationId()
                        .equals(contributions.organizationId())
                || contributions.projectionGeneration() != batch.generation()) {
            throw new IllegalArgumentException(
                    "graph revision must belong to the batch organization and generation");
        }
        support.stage(batch, ProjectionKind.GRAPH, COPY_PREDECESSOR, () -> {
            deleteRevision(batch.id(), contributions.sourceRevisionId());
            contributions.entities().forEach(entity -> upsertEntity(batch, entity));
            contributions.relations().forEach(relation -> upsertRelation(batch, relation));
            removeOrphans(batch.id());
        });
    }

    @Override
    public void stageDeleteRevision(
            ProjectionBatch batch,
            UUID sourceRevisionId) {
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        support.stage(batch, ProjectionKind.GRAPH, COPY_PREDECESSOR, () -> {
            deleteRevision(batch.id(), sourceRevisionId);
            removeOrphans(batch.id());
        });
    }

    @Override
    public List<CanonicalEntity> loadEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = ids(entityIds);
        if (!readable(scope, snapshot, ids)) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT entity.*
                FROM projection_graph_entities entity
                WHERE entity.batch_id = :batchId
                  AND entity.entity_id IN (:ids)
                  AND EXISTS (
                      SELECT 1
                      FROM (
                """
                        + VISIBLE_ENTITY_CONTRIBUTIONS
                        + """
                      ) visible
                      WHERE visible.entity_id = entity.entity_id
                  )
                ORDER BY entity.entity_id
                """,
                visibility(scope, snapshot).addValue("ids", ids),
                (resultSet, rowNumber) -> entity(resultSet));
    }

    @Override
    public List<CanonicalRelation> loadRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = ids(relationIds);
        if (!readable(scope, snapshot, ids)) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT relation.*
                FROM projection_graph_relations relation
                WHERE relation.batch_id = :batchId
                  AND relation.relation_id IN (:ids)
                  AND EXISTS (
                      SELECT 1
                      FROM (
                """
                        + VISIBLE_RELATION_CONTRIBUTIONS
                        + """
                      ) visible
                      WHERE visible.relation_id = relation.relation_id
                  )
                ORDER BY relation.relation_id
                """,
                visibility(scope, snapshot).addValue("ids", ids),
                (resultSet, rowNumber) -> relation(resultSet));
    }

    @Override
    public List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = ids(entityIds);
        if (!readable(scope, snapshot, ids)) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT contribution.*, entity.normalized_name
                FROM (
                """
                        + VISIBLE_ENTITY_CONTRIBUTIONS
                        + """
                ) contribution
                JOIN projection_graph_entities entity
                  ON entity.batch_id = contribution.batch_id
                 AND entity.entity_id = contribution.entity_id
                WHERE contribution.entity_id IN (:ids)
                ORDER BY contribution.contribution_id
                """,
                visibility(scope, snapshot).addValue("ids", ids),
                (resultSet, rowNumber) -> entityContribution(resultSet));
    }

    @Override
    public List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = ids(relationIds);
        if (!readable(scope, snapshot, ids)) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT contribution.*, relation.source_entity_id,
                       relation.target_entity_id, relation.orientation
                FROM (
                """
                        + VISIBLE_RELATION_CONTRIBUTIONS
                        + """
                ) contribution
                JOIN projection_graph_relations relation
                  ON relation.batch_id = contribution.batch_id
                 AND relation.relation_id = contribution.relation_id
                WHERE contribution.relation_id IN (:ids)
                ORDER BY contribution.contribution_id
                """,
                visibility(scope, snapshot).addValue("ids", ids),
                (resultSet, rowNumber) -> relationContribution(resultSet));
    }

    @Override
    public List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit) {
        List<UUID> ids = ids(entityIds);
        requireNonNegative(limit, "limit");
        if (limit == 0 || !readable(scope, snapshot, ids)) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT relation.*
                FROM projection_graph_relations relation
                WHERE relation.batch_id = :batchId
                  AND (
                      relation.source_entity_id IN (:ids)
                      OR relation.target_entity_id IN (:ids)
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM (
                """
                        + VISIBLE_RELATION_CONTRIBUTIONS
                        + """
                      ) visible
                      WHERE visible.relation_id = relation.relation_id
                  )
                ORDER BY relation.relation_id
                LIMIT :limit
                """,
                visibility(scope, snapshot)
                        .addValue("ids", ids)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> relation(resultSet));
    }

    @Override
    public Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = ids(entityIds);
        support.requireReadable(scope, snapshot, ProjectionKind.GRAPH);
        LinkedHashMap<UUID, Long> degrees = new LinkedHashMap<>();
        ids.stream().sorted().forEach(id -> degrees.put(id, 0L));
        if (ids.isEmpty() || PostgresProjectionSupport.noAuthorizedAssets(scope)) {
            return Map.copyOf(degrees);
        }
        jdbc.query(
                """
                SELECT endpoint.entity_id, count(DISTINCT relation.relation_id) AS degree
                FROM projection_graph_relations relation
                CROSS JOIN LATERAL (
                    VALUES (relation.source_entity_id), (relation.target_entity_id)
                ) endpoint(entity_id)
                WHERE relation.batch_id = :batchId
                  AND endpoint.entity_id IN (:ids)
                  AND EXISTS (
                      SELECT 1
                      FROM (
                """
                        + VISIBLE_RELATION_CONTRIBUTIONS
                        + """
                      ) visible
                      WHERE visible.relation_id = relation.relation_id
                  )
                GROUP BY endpoint.entity_id
                """,
                visibility(scope, snapshot).addValue("ids", ids),
                (RowCallbackHandler) resultSet -> degrees.put(
                        resultSet.getObject("entity_id", UUID.class),
                        resultSet.getLong("degree")));
        return Map.copyOf(degrees);
    }

    @Override
    public Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = ids(relationIds);
        if (!readable(scope, snapshot, ids)) {
            return Map.of();
        }
        Map<UUID, Double> weights = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT relation_id, sum(weight) AS weight
                FROM (
                """
                        + VISIBLE_RELATION_CONTRIBUTIONS
                        + """
                ) visible
                WHERE relation_id IN (:ids)
                GROUP BY relation_id
                ORDER BY relation_id
                """,
                visibility(scope, snapshot).addValue("ids", ids),
                (RowCallbackHandler) resultSet -> weights.put(
                        resultSet.getObject("relation_id", UUID.class),
                        resultSet.getDouble("weight")));
        return Map.copyOf(weights);
    }

    @Override
    public List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        List<UUID> seeds = ids(seedEntityIds);
        requireNonNegative(maximumDepth, "maximumDepth");
        requireNonNegative(limit, "limit");
        if (limit == 0 || !readable(scope, snapshot, seeds)) {
            return List.of();
        }
        return jdbc.query(
                """
                WITH RECURSIVE
                visible_relations AS (
                    SELECT DISTINCT relation.*
                    FROM projection_graph_relations relation
                    JOIN (
                """
                        + VISIBLE_RELATION_CONTRIBUTIONS
                        + """
                    ) visible
                      ON visible.batch_id = relation.batch_id
                     AND visible.relation_id = relation.relation_id
                    WHERE relation.batch_id = :batchId
                ),
                walk(entity_id, depth, path) AS (
                    SELECT seed.entity_id, 0, ARRAY[seed.entity_id]
                    FROM unnest(ARRAY[:seedIds]::uuid[]) seed(entity_id)
                    UNION ALL
                    SELECT
                        CASE
                            WHEN relation.source_entity_id = walk.entity_id
                            THEN relation.target_entity_id
                            ELSE relation.source_entity_id
                        END,
                        walk.depth + 1,
                        walk.path || CASE
                            WHEN relation.source_entity_id = walk.entity_id
                            THEN relation.target_entity_id
                            ELSE relation.source_entity_id
                        END
                    FROM walk
                    JOIN visible_relations relation
                      ON relation.source_entity_id = walk.entity_id
                      OR relation.target_entity_id = walk.entity_id
                    WHERE walk.depth < :maximumDepth
                      AND NOT (
                          CASE
                              WHEN relation.source_entity_id = walk.entity_id
                              THEN relation.target_entity_id
                              ELSE relation.source_entity_id
                          END = ANY(walk.path)
                      )
                )
                SELECT entity_id
                FROM walk
                GROUP BY entity_id
                ORDER BY min(depth), entity_id
                LIMIT :limit
                """,
                visibility(scope, snapshot)
                        .addValue("seedIds", seeds)
                        .addValue("maximumDepth", maximumDepth)
                        .addValue("limit", limit),
                (resultSet, rowNumber) ->
                        resultSet.getObject("entity_id", UUID.class));
    }

    @Override
    public void discard(ProjectionBatch batch) {
        support.discard(
                batch,
                ProjectionKind.GRAPH,
                List.of(
                        """
                        DELETE FROM projection_graph_relation_contributions
                        WHERE batch_id = :batchId
                        """,
                        """
                        DELETE FROM projection_graph_entity_contributions
                        WHERE batch_id = :batchId
                        """,
                        """
                        DELETE FROM projection_graph_relations
                        WHERE batch_id = :batchId
                        """,
                        """
                        DELETE FROM projection_graph_entities
                        WHERE batch_id = :batchId
                        """));
    }

    private void upsertEntity(
            ProjectionBatch batch,
            EntityContribution contribution) {
        jdbc.update(
                """
                INSERT INTO projection_graph_entities (
                    batch_id, entity_id, normalized_name)
                VALUES (:batchId, :entityId, :normalizedName)
                ON CONFLICT (batch_id, entity_id)
                DO UPDATE SET normalized_name = EXCLUDED.normalized_name
                """,
                Map.of(
                        "batchId", batch.id(),
                        "entityId", contribution.entity().id(),
                        "normalizedName", contribution.entity().normalizedName()));
        jdbc.update(
                """
                INSERT INTO projection_graph_entity_contributions (
                    batch_id, contribution_id, entity_id, entity_type, description,
                    organization_id, knowledge_asset_id, source_revision_id, chunk_id,
                    acl_snapshot_id, acl_generation, projection_generation,
                    extractor_provider, extractor_model, prompt_version,
                    extraction_profile_fingerprint, confidence, extracted_at)
                VALUES (
                    :batchId, :contributionId, :entityId, :entityType, :description,
                    :organizationId, :knowledgeAssetId, :sourceRevisionId, :chunkId,
                    :aclSnapshotId, :aclGeneration, :projectionGeneration,
                    :extractorProvider, :extractorModel, :promptVersion,
                    :extractionProfileFingerprint, :confidence, :extractedAt)
                ON CONFLICT (batch_id, contribution_id)
                DO UPDATE SET
                    entity_id = EXCLUDED.entity_id,
                    entity_type = EXCLUDED.entity_type,
                    description = EXCLUDED.description,
                    organization_id = EXCLUDED.organization_id,
                    knowledge_asset_id = EXCLUDED.knowledge_asset_id,
                    source_revision_id = EXCLUDED.source_revision_id,
                    chunk_id = EXCLUDED.chunk_id,
                    acl_snapshot_id = EXCLUDED.acl_snapshot_id,
                    acl_generation = EXCLUDED.acl_generation,
                    projection_generation = EXCLUDED.projection_generation,
                    extractor_provider = EXCLUDED.extractor_provider,
                    extractor_model = EXCLUDED.extractor_model,
                    prompt_version = EXCLUDED.prompt_version,
                    extraction_profile_fingerprint =
                        EXCLUDED.extraction_profile_fingerprint,
                    confidence = EXCLUDED.confidence,
                    extracted_at = EXCLUDED.extracted_at
                """,
                contributionParameters(batch, contribution));
    }

    private void upsertRelation(
            ProjectionBatch batch,
            RelationContribution contribution) {
        jdbc.update(
                """
                INSERT INTO projection_graph_relations (
                    batch_id, relation_id, source_entity_id, target_entity_id, orientation)
                VALUES (
                    :batchId, :relationId, :sourceEntityId, :targetEntityId, :orientation)
                ON CONFLICT (batch_id, relation_id)
                DO UPDATE SET
                    source_entity_id = EXCLUDED.source_entity_id,
                    target_entity_id = EXCLUDED.target_entity_id,
                    orientation = EXCLUDED.orientation
                """,
                relationParameters(batch, contribution));
        jdbc.update(
                """
                INSERT INTO projection_graph_relation_contributions (
                    batch_id, contribution_id, relation_id, relation_type, keywords,
                    description, weight, organization_id, knowledge_asset_id,
                    source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                    projection_generation, extractor_provider, extractor_model,
                    prompt_version, extraction_profile_fingerprint, confidence, extracted_at)
                VALUES (
                    :batchId, :contributionId, :relationId, :relationType, :keywords,
                    :description, :weight, :organizationId, :knowledgeAssetId,
                    :sourceRevisionId, :chunkId, :aclSnapshotId, :aclGeneration,
                    :projectionGeneration, :extractorProvider, :extractorModel,
                    :promptVersion, :extractionProfileFingerprint, :confidence, :extractedAt)
                ON CONFLICT (batch_id, contribution_id)
                DO UPDATE SET
                    relation_id = EXCLUDED.relation_id,
                    relation_type = EXCLUDED.relation_type,
                    keywords = EXCLUDED.keywords,
                    description = EXCLUDED.description,
                    weight = EXCLUDED.weight,
                    organization_id = EXCLUDED.organization_id,
                    knowledge_asset_id = EXCLUDED.knowledge_asset_id,
                    source_revision_id = EXCLUDED.source_revision_id,
                    chunk_id = EXCLUDED.chunk_id,
                    acl_snapshot_id = EXCLUDED.acl_snapshot_id,
                    acl_generation = EXCLUDED.acl_generation,
                    projection_generation = EXCLUDED.projection_generation,
                    extractor_provider = EXCLUDED.extractor_provider,
                    extractor_model = EXCLUDED.extractor_model,
                    prompt_version = EXCLUDED.prompt_version,
                    extraction_profile_fingerprint =
                        EXCLUDED.extraction_profile_fingerprint,
                    confidence = EXCLUDED.confidence,
                    extracted_at = EXCLUDED.extracted_at
                """,
                contributionParameters(batch, contribution));
    }

    private void deleteRevision(UUID batchId, UUID sourceRevisionId) {
        Map<String, UUID> parameters = Map.of(
                "batchId", batchId,
                "sourceRevisionId", sourceRevisionId);
        jdbc.update(
                """
                DELETE FROM projection_graph_relation_contributions
                WHERE batch_id = :batchId
                  AND source_revision_id = :sourceRevisionId
                """,
                parameters);
        jdbc.update(
                """
                DELETE FROM projection_graph_entity_contributions
                WHERE batch_id = :batchId
                  AND source_revision_id = :sourceRevisionId
                """,
                parameters);
    }

    private void removeOrphans(UUID batchId) {
        jdbc.update(
                """
                DELETE FROM projection_graph_relations relation
                WHERE relation.batch_id = :batchId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM projection_graph_relation_contributions contribution
                      WHERE contribution.batch_id = relation.batch_id
                        AND contribution.relation_id = relation.relation_id
                  )
                """,
                Map.of("batchId", batchId));
        jdbc.update(
                """
                DELETE FROM projection_graph_entities entity
                WHERE entity.batch_id = :batchId
                  AND NOT EXISTS (
                      SELECT 1
                      FROM projection_graph_entity_contributions contribution
                      WHERE contribution.batch_id = entity.batch_id
                        AND contribution.entity_id = entity.entity_id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM projection_graph_relations relation
                      WHERE relation.batch_id = entity.batch_id
                        AND (
                            relation.source_entity_id = entity.entity_id
                            OR relation.target_entity_id = entity.entity_id
                        )
                  )
                """,
                Map.of("batchId", batchId));
    }

    private boolean readable(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> ids) {
        support.requireReadable(scope, snapshot, ProjectionKind.GRAPH);
        return !ids.isEmpty()
                && !PostgresProjectionSupport.noAuthorizedAssets(scope);
    }

    private static List<UUID> ids(Collection<UUID> ids) {
        List<UUID> immutable = List.copyOf(Objects.requireNonNull(ids, "ids"));
        if (immutable.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ids must not contain null");
        }
        return immutable;
    }

    private static MapSqlParameterSource visibility(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        return PostgresProjectionSupport.visibilityParameters(scope, snapshot)
                .addValue("generation", snapshot.generation());
    }

    private static CanonicalEntity entity(ResultSet resultSet)
            throws SQLException {
        return new CanonicalEntity(
                resultSet.getObject("entity_id", UUID.class),
                resultSet.getString("normalized_name"));
    }

    private static CanonicalRelation relation(ResultSet resultSet)
            throws SQLException {
        return new CanonicalRelation(
                resultSet.getObject("relation_id", UUID.class),
                resultSet.getObject("source_entity_id", UUID.class),
                resultSet.getObject("target_entity_id", UUID.class),
                RelationOrientation.valueOf(resultSet.getString("orientation")));
    }

    private static EntityContribution entityContribution(ResultSet resultSet)
            throws SQLException {
        return new EntityContribution(
                resultSet.getObject("contribution_id", UUID.class),
                new CanonicalEntity(
                        resultSet.getObject("entity_id", UUID.class),
                        resultSet.getString("normalized_name")),
                resultSet.getString("entity_type"),
                resultSet.getString("description"),
                provenance(resultSet));
    }

    private static RelationContribution relationContribution(ResultSet resultSet)
            throws SQLException {
        return new RelationContribution(
                resultSet.getObject("contribution_id", UUID.class),
                new CanonicalRelation(
                        resultSet.getObject("relation_id", UUID.class),
                        resultSet.getObject("source_entity_id", UUID.class),
                        resultSet.getObject("target_entity_id", UUID.class),
                        RelationOrientation.valueOf(
                                resultSet.getString("orientation"))),
                resultSet.getString("relation_type"),
                PostgresProjectionCodec.decodeList(
                        resultSet.getString("keywords")),
                resultSet.getString("description"),
                resultSet.getDouble("weight"),
                provenance(resultSet));
    }

    private static EvidenceProvenance provenance(ResultSet resultSet)
            throws SQLException {
        return new EvidenceProvenance(
                PostgresProjectionSupport.evidence(resultSet),
                resultSet.getLong("projection_generation"),
                resultSet.getString("extractor_provider"),
                resultSet.getString("extractor_model"),
                resultSet.getString("prompt_version"),
                resultSet.getString("extraction_profile_fingerprint"),
                resultSet.getDouble("confidence"),
                resultSet.getTimestamp("extracted_at").toInstant());
    }

    private static MapSqlParameterSource contributionParameters(
            ProjectionBatch batch,
            EntityContribution contribution) {
        return provenanceParameters(batch, contribution.provenance())
                .addValue("contributionId", contribution.id())
                .addValue("entityId", contribution.entity().id())
                .addValue("entityType", contribution.type())
                .addValue("description", contribution.description());
    }

    private static MapSqlParameterSource contributionParameters(
            ProjectionBatch batch,
            RelationContribution contribution) {
        return relationParameters(batch, contribution)
                .addValues(provenanceParameters(batch, contribution.provenance()).getValues())
                .addValue("contributionId", contribution.id())
                .addValue("relationType", contribution.type())
                .addValue(
                        "keywords",
                        PostgresProjectionCodec.encodeList(contribution.keywords()))
                .addValue("description", contribution.description())
                .addValue("weight", contribution.weight());
    }

    private static MapSqlParameterSource relationParameters(
            ProjectionBatch batch,
            RelationContribution contribution) {
        return new MapSqlParameterSource()
                .addValue("batchId", batch.id())
                .addValue("relationId", contribution.relation().id())
                .addValue(
                        "sourceEntityId",
                        contribution.relation().sourceEntityId())
                .addValue(
                        "targetEntityId",
                        contribution.relation().targetEntityId())
                .addValue(
                        "orientation",
                        contribution.relation().orientation().name());
    }

    private static MapSqlParameterSource provenanceParameters(
            ProjectionBatch batch,
            EvidenceProvenance provenance) {
        return PostgresProjectionSupport.evidenceParameters(provenance.evidence())
                .addValue("batchId", batch.id())
                .addValue(
                        "projectionGeneration",
                        provenance.projectionGeneration())
                .addValue("extractorProvider", provenance.extractorProvider())
                .addValue("extractorModel", provenance.extractorModel())
                .addValue("promptVersion", provenance.promptVersion())
                .addValue(
                        "extractionProfileFingerprint",
                        provenance.extractionProfileFingerprint())
                .addValue("confidence", provenance.confidence())
                .addValue(
                        "extractedAt",
                        Timestamp.from(provenance.extractedAt()));
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
