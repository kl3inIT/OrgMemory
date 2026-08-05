package com.orgmemory.graphrag.postgres;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deterministic, disposable 1536-dimensional projection fixture for the ADR 0020 gate. */
final class MultiSnapshotSyntheticDataset {

    static final int SPACE_COUNT = 20;
    static final int ASSETS_PER_SPACE = 2;
    static final int BASE_ENTITIES_PER_ASSET = 2;
    static final int DIMENSIONS = 1536;
    static final long FIXED_SEED = 0x4f52474d454d4f52L;
    static final UUID ORGANIZATION_ID = id("msq-benchmark-organization");
    static final UUID EMBEDDING_PROFILE_ID = id("msq-benchmark-embedding-profile");
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final int JDBC_BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    MultiSnapshotSyntheticDataset(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    Dataset load(Scale scale) {
        jdbc.execute("TRUNCATE TABLE projection_batches CASCADE");
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 0)
                ON CONFLICT (id) DO NOTHING
                """,
                ORGANIZATION_ID,
                "MSQ benchmark organization",
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT));

        List<SpaceData> spaces = new ArrayList<>();
        List<VectorRow> vectors = new ArrayList<>();
        List<EntityRow> entities = new ArrayList<>();
        List<EntityContributionRow> entityContributions = new ArrayList<>();
        List<RelationRow> relations = new ArrayList<>();
        List<RelationContributionRow> relationContributions = new ArrayList<>();
        int entitiesPerAsset = BASE_ENTITIES_PER_ASSET * scale.multiplier();

        for (int spaceIndex = 0; spaceIndex < SPACE_COUNT; spaceIndex++) {
            UUID spaceId = id("space-" + spaceIndex);
            UUID batchId = id(scale.label() + "-batch-" + spaceIndex);
            String manifest = "msq-" + scale.label() + "-space-" + spaceIndex;
            List<UUID> assetIds = new ArrayList<>();
            insertPublication(spaceId, batchId, manifest);

            for (int assetIndex = 0; assetIndex < ASSETS_PER_SPACE; assetIndex++) {
                UUID assetId = id("space-" + spaceIndex + "-asset-" + assetIndex);
                UUID revisionId = id(scale.label() + "-revision-" + spaceIndex + "-" + assetIndex);
                UUID aclId = id(scale.label() + "-acl-" + spaceIndex + "-" + assetIndex);
                assetIds.add(assetId);
                List<UUID> assetEntities = new ArrayList<>();

                for (int entityIndex = 0; entityIndex < entitiesPerAsset; entityIndex++) {
                    String coordinate = scale.label() + "-" + spaceIndex + "-" + assetIndex
                            + "-" + entityIndex;
                    UUID entityId = id("entity-" + coordinate);
                    UUID contributionId = id("entity-contribution-" + coordinate);
                    UUID chunkId = id("chunk-" + coordinate);
                    assetEntities.add(entityId);
                    entities.add(new EntityRow(batchId, entityId, "entity-" + coordinate));
                    entityContributions.add(new EntityContributionRow(
                            batchId,
                            contributionId,
                            entityId,
                            assetId,
                            revisionId,
                            chunkId,
                            aclId));
                    vectors.add(new VectorRow(
                            batchId,
                            "entity:" + entityId,
                            entityId.toString(),
                            assetId,
                            revisionId,
                            chunkId,
                            aclId,
                            vector(coordinate)));
                }

                for (int relationIndex = 0;
                        relationIndex + 1 < assetEntities.size();
                        relationIndex++) {
                    String coordinate = scale.label() + "-" + spaceIndex + "-" + assetIndex
                            + "-" + relationIndex;
                    UUID relationId = id("relation-" + coordinate);
                    UUID contributionId = id("relation-contribution-" + coordinate);
                    UUID chunkId = id("relation-chunk-" + coordinate);
                    relations.add(new RelationRow(
                            batchId,
                            relationId,
                            assetEntities.get(relationIndex),
                            assetEntities.get(relationIndex + 1)));
                    relationContributions.add(new RelationContributionRow(
                            batchId,
                            contributionId,
                            relationId,
                            assetId,
                            revisionId,
                            chunkId,
                            aclId));
                }
            }
            spaces.add(new SpaceData(spaceId, batchId, manifest, List.copyOf(assetIds)));
        }

        insertEntities(entities);
        insertEntityContributions(entityContributions);
        insertRelations(relations);
        insertRelationContributions(relationContributions);
        insertVectors(vectors);
        jdbc.execute("ANALYZE projection_vector_records");
        jdbc.execute("ANALYZE projection_graph_entity_contributions");
        jdbc.execute("ANALYZE projection_graph_relation_contributions");
        jdbc.execute("ANALYZE projection_graph_relations");

        return new Dataset(
                scale,
                spaces.stream().sorted(Comparator.comparing(SpaceData::spaceId)).toList(),
                queryVector(),
                vectors.size(),
                entityContributions.size(),
                relationContributions.size());
    }

    private void insertPublication(UUID spaceId, UUID batchId, String manifest) {
        jdbc.update(
                """
                INSERT INTO projection_batches (
                    batch_id, organization_id, workspace, collection_name,
                    expected_previous_generation, generation, idempotency_key,
                    manifest_fingerprint, required_projections, status,
                    created_at, published_at)
                VALUES (?, ?, 'default', ?, 0, 1, ?, ?, 'GRAPH,VECTOR',
                        'PUBLISHED', ?, ?)
                """,
                batchId,
                ORGANIZATION_ID,
                spaceId.toString(),
                "msq-" + batchId,
                manifest,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT));
        jdbc.update(
                """
                INSERT INTO projection_publications (
                    batch_id, organization_id, workspace, collection_name,
                    generation, manifest_fingerprint, projections, published_at)
                VALUES (?, ?, 'default', ?, 1, ?, 'GRAPH,VECTOR', ?)
                """,
                batchId,
                ORGANIZATION_ID,
                spaceId.toString(),
                manifest,
                Timestamp.from(CREATED_AT));
    }

    private void insertEntities(List<EntityRow> rows) {
        batchUpdate(
                """
                INSERT INTO projection_graph_entities (batch_id, entity_id, normalized_name)
                VALUES (?, ?, ?)
                """,
                rows,
                (statement, row) -> {
                    statement.setObject(1, row.batchId());
                    statement.setObject(2, row.entityId());
                    statement.setString(3, row.normalizedName());
                });
    }

    private void insertEntityContributions(List<EntityContributionRow> rows) {
        batchUpdate(
                """
                INSERT INTO projection_graph_entity_contributions (
                    batch_id, contribution_id, entity_id, entity_type, description,
                    organization_id, knowledge_asset_id, source_revision_id, chunk_id,
                    acl_snapshot_id, acl_generation, projection_generation,
                    extractor_provider, extractor_model, prompt_version,
                    extraction_profile_fingerprint, confidence, extracted_at)
                VALUES (?, ?, ?, 'SYNTHETIC', 'synthetic entity contribution',
                        ?, ?, ?, ?, ?, 1, 1,
                        'fixture', 'fixture', 'v1', 'fixture-fingerprint', 1.0, ?)
                """,
                rows,
                (statement, row) -> {
                    statement.setObject(1, row.batchId());
                    statement.setObject(2, row.contributionId());
                    statement.setObject(3, row.entityId());
                    statement.setObject(4, ORGANIZATION_ID);
                    statement.setObject(5, row.assetId());
                    statement.setObject(6, row.revisionId());
                    statement.setObject(7, row.chunkId());
                    statement.setObject(8, row.aclId());
                    statement.setTimestamp(9, Timestamp.from(CREATED_AT));
                });
    }

    private void insertRelations(List<RelationRow> rows) {
        batchUpdate(
                """
                INSERT INTO projection_graph_relations (
                    batch_id, relation_id, source_entity_id, target_entity_id, orientation)
                VALUES (?, ?, ?, ?, 'UNDIRECTED')
                """,
                rows,
                (statement, row) -> {
                    statement.setObject(1, row.batchId());
                    statement.setObject(2, row.relationId());
                    statement.setObject(3, row.sourceEntityId());
                    statement.setObject(4, row.targetEntityId());
                });
    }

    private void insertRelationContributions(List<RelationContributionRow> rows) {
        batchUpdate(
                """
                INSERT INTO projection_graph_relation_contributions (
                    batch_id, contribution_id, relation_id, relation_type, keywords,
                    description, weight, organization_id, knowledge_asset_id,
                    source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                    projection_generation, extractor_provider, extractor_model,
                    prompt_version, extraction_profile_fingerprint, confidence, extracted_at)
                VALUES (?, ?, ?, 'SYNTHETIC', 'synthetic',
                        'synthetic relation contribution', 1.0, ?, ?, ?, ?, ?, 1,
                        1, 'fixture', 'fixture', 'v1', 'fixture-fingerprint', 1.0, ?)
                """,
                rows,
                (statement, row) -> {
                    statement.setObject(1, row.batchId());
                    statement.setObject(2, row.contributionId());
                    statement.setObject(3, row.relationId());
                    statement.setObject(4, ORGANIZATION_ID);
                    statement.setObject(5, row.assetId());
                    statement.setObject(6, row.revisionId());
                    statement.setObject(7, row.chunkId());
                    statement.setObject(8, row.aclId());
                    statement.setTimestamp(9, Timestamp.from(CREATED_AT));
                });
    }

    private void insertVectors(List<VectorRow> rows) {
        batchUpdate(
                """
                INSERT INTO projection_vector_records (
                    batch_id, record_id, subject_id, organization_id,
                    knowledge_asset_id, source_revision_id, chunk_id,
                    acl_snapshot_id, acl_generation, vector_kind,
                    embedding_profile_id, model, dimensions, embedding, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 'ENTITY', ?, 'fixture', 1536,
                        CAST(? AS vector), '{}')
                """,
                rows,
                (statement, row) -> {
                    statement.setObject(1, row.batchId());
                    statement.setString(2, row.recordId());
                    statement.setString(3, row.subjectId());
                    statement.setObject(4, ORGANIZATION_ID);
                    statement.setObject(5, row.assetId());
                    statement.setObject(6, row.revisionId());
                    statement.setObject(7, row.chunkId());
                    statement.setObject(8, row.aclId());
                    statement.setObject(9, EMBEDDING_PROFILE_ID);
                    statement.setString(10, row.vector());
                });
    }

    private <T> void batchUpdate(String sql, List<T> rows, StatementBinder<T> binder) {
        jdbc.batchUpdate(
                sql,
                rows,
                JDBC_BATCH_SIZE,
                (statement, row) -> binder.bind(statement, row));
    }

    private static String vector(String coordinate) {
        SplittableRandom random = new SplittableRandom(FIXED_SEED ^ coordinate.hashCode());
        StringBuilder encoded = new StringBuilder(DIMENSIONS * 2).append('[');
        for (int dimension = 0; dimension < DIMENSIONS; dimension++) {
            if (dimension > 0) {
                encoded.append(',');
            }
            if (dimension < 8) {
                encoded.append(0.25 + random.nextDouble(0.75));
            } else {
                encoded.append('0');
            }
        }
        return encoded.append(']').toString();
    }

    private static String queryVector() {
        StringBuilder encoded = new StringBuilder(DIMENSIONS * 2).append('[');
        for (int dimension = 0; dimension < DIMENSIONS; dimension++) {
            if (dimension > 0) {
                encoded.append(',');
            }
            encoded.append(dimension == 0 ? '1' : '0');
        }
        return encoded.append(']').toString();
    }

    static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    enum Scale {
        CURRENT("1x", 1),
        TEN_X("10x", 10),
        HUNDRED_X("100x", 100);

        private final String label;
        private final int multiplier;

        Scale(String label, int multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        String label() {
            return label;
        }

        int multiplier() {
            return multiplier;
        }
    }

    enum Grant {
        NARROW,
        BROAD
    }

    record Dataset(
            Scale scale,
            List<SpaceData> spaces,
            String queryVector,
            int vectorCount,
            int entityContributionCount,
            int relationContributionCount) {

        Dataset {
            spaces = List.copyOf(spaces);
        }

        AuthorizedMultiSnapshotQuery.Request request(int spaceCount, Grant grant) {
            if (spaceCount <= 0 || spaceCount > spaces.size()) {
                throw new IllegalArgumentException("spaceCount is outside the fixture");
            }
            List<AuthorizedMultiSnapshotQuery.SnapshotScope> scopes = spaces.stream()
                    .limit(spaceCount)
                    .map(space -> new AuthorizedMultiSnapshotQuery.SnapshotScope(
                            space.spaceId(),
                            space.batchId(),
                            1,
                            space.manifestFingerprint(),
                            1,
                            grant == Grant.NARROW
                                    ? List.of(space.assetIds().getFirst())
                                    : space.assetIds()))
                    .toList();
            return new AuthorizedMultiSnapshotQuery.Request(
                    ORGANIZATION_ID,
                    EMBEDDING_PROFILE_ID,
                    queryVector,
                    40,
                    40,
                    -1.0,
                    scopes);
        }
    }

    record SpaceData(
            UUID spaceId,
            UUID batchId,
            String manifestFingerprint,
            List<UUID> assetIds) {

        SpaceData {
            assetIds = List.copyOf(assetIds);
        }
    }

    private record VectorRow(
            UUID batchId,
            String recordId,
            String subjectId,
            UUID assetId,
            UUID revisionId,
            UUID chunkId,
            UUID aclId,
            String vector) {}

    private record EntityRow(UUID batchId, UUID entityId, String normalizedName) {}

    private record EntityContributionRow(
            UUID batchId,
            UUID contributionId,
            UUID entityId,
            UUID assetId,
            UUID revisionId,
            UUID chunkId,
            UUID aclId) {}

    private record RelationRow(
            UUID batchId,
            UUID relationId,
            UUID sourceEntityId,
            UUID targetEntityId) {}

    private record RelationContributionRow(
            UUID batchId,
            UUID contributionId,
            UUID relationId,
            UUID assetId,
            UUID revisionId,
            UUID chunkId,
            UUID aclId) {}

    @FunctionalInterface
    private interface StatementBinder<T> {
        void bind(PreparedStatement statement, T row) throws SQLException;
    }
}
