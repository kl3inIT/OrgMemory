package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.CurationProvenance;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphIdentityRef;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.ContributionEmbedding;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.port.GraphRevisionEmbeddings;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresGraphProjectionStoreIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID SHARED_ENTITY_ID = id("shared-entity");
    private static final UUID PUBLIC_NEIGHBOR_ID = id("public-neighbor");
    private static final UUID SECRET_NEIGHBOR_ID = id("secret-neighbor");
    private static final UUID PUBLIC_RELATION_ID = id("public-relation");
    private static final UUID SECRET_RELATION_ID = id("secret-relation");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static JdbcTemplate jdbc;
    private static PostgresGraphProjectionStore store;
    private static PostgresGraphRagCacheStore cacheStore;
    private static PostgresGraphCurationStore curationStore;
    private static PostgresGraphExportReader exportReader;
    private static OrganizationFixture primaryOrganization;
    private static OrganizationFixture otherOrganization;
    private static AssetFixture allowedAsset;
    private static AssetFixture restrictedAsset;
    private static AssetFixture replacementAsset;
    private static AssetFixture atomicPublicationAsset;
    private static AssetFixture otherTenantAsset;

    @BeforeAll
    static void migrateAndSeed() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("31")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        OrganizationFixture migrationOrganization = seedOrganization("migration");
        AssetFixture migrationAsset = seedAsset(migrationOrganization, "migration");
        seedPreV32GraphProjection(migrationAsset);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertEvidenceScopedGraphMigration(migrationAsset);

        store = new PostgresGraphProjectionStore(
                new NamedParameterJdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource),
                CLOCK);
        cacheStore = new PostgresGraphRagCacheStore(
                new NamedParameterJdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource));
        curationStore = new PostgresGraphCurationStore(
                new NamedParameterJdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource));
        exportReader = new PostgresGraphExportReader(
                new NamedParameterJdbcTemplate(dataSource),
                store,
                curationStore);

        primaryOrganization = seedOrganization("primary");
        otherOrganization = seedOrganization("other");
        allowedAsset = seedAsset(primaryOrganization, "allowed");
        restrictedAsset = seedAsset(primaryOrganization, "restricted");
        replacementAsset = seedAsset(primaryOrganization, "replacement");
        atomicPublicationAsset = seedAsset(primaryOrganization, "atomic-publication");
        otherTenantAsset = seedAsset(otherOrganization, "other-tenant");

        store.replaceRevision(publicProjection(allowedAsset, 1, allowedAsset.chunkId()));
        store.replaceRevision(restrictedProjection(restrictedAsset));
        store.replaceRevision(
                publicProjection(replacementAsset, 1, replacementAsset.chunkId()));
        store.replaceRevision(publicProjection(
                atomicPublicationAsset, 1, atomicPublicationAsset.chunkId()));
        store.replaceRevision(publicProjection(otherTenantAsset, 1, otherTenantAsset.chunkId()));
        store.replaceRevisionEmbeddings(publicEmbeddings(allowedAsset, 1));
        store.replaceRevisionEmbeddings(restrictedEmbeddings(restrictedAsset));
        store.replaceRevisionEmbeddings(publicEmbeddings(atomicPublicationAsset, 1));
        store.replaceRevisionEmbeddings(publicEmbeddings(otherTenantAsset, 1));
    }

    private static void seedPreV32GraphProjection(AssetFixture fixture) {
        UUID sourceEntityId = id("migration-source-entity");
        UUID targetEntityId = id("migration-target-entity");
        UUID relationId = id("migration-relation");
        jdbc.update("""
                INSERT INTO graph_entities (
                    organization_id, id, normalized_name, entity_type,
                    created_at, updated_at)
                VALUES
                    (?, ?, 'Legacy Source', 'SYSTEM', now(), now()),
                    (?, ?, 'Legacy Target', 'POLICY', now(), now())
                """,
                fixture.organizationId(),
                sourceEntityId,
                fixture.organizationId(),
                targetEntityId);
        jdbc.update("""
                INSERT INTO graph_relations (
                    organization_id, id, source_entity_id, target_entity_id,
                    relation_type, orientation, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'GOVERNS', 'DIRECTED', now(), now())
                """,
                fixture.organizationId(),
                relationId,
                sourceEntityId,
                targetEntityId);
        jdbc.update("""
                INSERT INTO graph_projection_heads (
                    organization_id, source_revision_id, knowledge_asset_id,
                    projection_generation, published_at)
                VALUES (?, ?, ?, 1, now())
                """,
                fixture.organizationId(),
                fixture.sourceRevisionId(),
                fixture.knowledgeAssetId());
        jdbc.update("""
                INSERT INTO graph_entity_contributions (
                    organization_id, id, entity_id, knowledge_asset_id,
                    source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                    projection_generation, description, extractor_provider,
                    extractor_model, prompt_version, confidence, extracted_at)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, 1, 1, 'Legacy source evidence',
                        'openai', 'gpt-5.6-sol', 'legacy-v1', 0.9, now()),
                    (?, ?, ?, ?, ?, ?, ?, 1, 1, 'Legacy target evidence',
                        'openai', 'gpt-5.6-sol', 'legacy-v1', 0.8, now())
                """,
                fixture.organizationId(),
                id("migration-source-contribution"),
                sourceEntityId,
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                fixture.chunkId(),
                fixture.aclSnapshotId(),
                fixture.organizationId(),
                id("migration-target-contribution"),
                targetEntityId,
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                fixture.chunkId(),
                fixture.aclSnapshotId());
        jdbc.update("""
                INSERT INTO graph_relation_contributions (
                    organization_id, id, relation_id, knowledge_asset_id,
                    source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                    projection_generation, keywords, description, search_content,
                    extractor_provider, extractor_model, prompt_version,
                    confidence, extracted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 1, ARRAY['governance'],
                    'Legacy relationship evidence',
                    'governance Legacy relationship evidence',
                    'openai', 'gpt-5.6-sol', 'legacy-v1', 0.7, now())
                """,
                fixture.organizationId(),
                id("migration-relation-contribution"),
                relationId,
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                fixture.chunkId(),
                fixture.aclSnapshotId());
    }

    private static void assertEvidenceScopedGraphMigration(AssetFixture fixture) {
        assertEquals(
                List.of("POLICY", "SYSTEM"),
                jdbc.queryForList(
                        """
                        SELECT entity_type
                        FROM graph_entity_contributions
                        WHERE organization_id = ?
                        ORDER BY entity_type
                        """,
                        String.class,
                        fixture.organizationId()));
        assertEquals(
                "GOVERNS",
                jdbc.queryForObject(
                        """
                        SELECT relation_type
                        FROM graph_relation_contributions
                        WHERE organization_id = ?
                        """,
                        String.class,
                        fixture.organizationId()));
        assertEquals(
                1.0,
                jdbc.queryForObject(
                        """
                        SELECT weight
                        FROM graph_relation_contributions
                        WHERE organization_id = ?
                        """,
                        Double.class,
                        fixture.organizationId()));
        assertEquals(
                3,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM (
                            SELECT search_vector
                            FROM graph_entity_contributions
                            WHERE organization_id = ?
                            UNION ALL
                            SELECT search_vector
                            FROM graph_relation_contributions
                            WHERE organization_id = ?
                        ) migrated
                        WHERE search_vector IS NOT NULL
                        """,
                        Integer.class,
                        fixture.organizationId(),
                        fixture.organizationId()));
    }

    @Test
    void filtersEvidenceBeforeTextVectorAndGraphAggregation() {
        AuthorizedEvidenceScope allowedOnly = scope(
                primaryOrganization,
                Set.of(allowedAsset.knowledgeAssetId()));
        AuthorizedEvidenceScope allPrimaryEvidence = scope(
                primaryOrganization,
                Set.of(allowedAsset.knowledgeAssetId(), restrictedAsset.knowledgeAssetId()));

        assertEquals(
                List.of("Approved secure retrieval evidence."),
                store.loadEntityContributions(allowedOnly, List.of(SHARED_ENTITY_ID))
                        .stream()
                        .map(EntityContribution::description)
                        .toList());
        assertTrue(store.searchEntities(allowedOnly, "nightfall", 10).isEmpty());
        assertTrue(store.searchRelations(allowedOnly, "acquisition", 10).isEmpty());
        assertEquals(
                List.of(PUBLIC_RELATION_ID),
                store.searchRelations(allowedOnly, "builds", 10).stream()
                        .map(result -> result.value().id())
                        .toList());
        assertEquals(
                List.of("BUILDS"),
                store.loadRelationContributions(
                                allowedOnly, List.of(PUBLIC_RELATION_ID))
                        .stream()
                        .map(RelationContribution::type)
                        .toList());
        assertEquals(
                List.of(PUBLIC_RELATION_ID),
                store.loadIncidentRelations(allowedOnly, List.of(SHARED_ENTITY_ID), 10)
                        .stream()
                        .map(CanonicalRelation::id)
                        .toList());
        assertEquals(
                List.of(PUBLIC_NEIGHBOR_ID),
                store.expandEntityIds(allowedOnly, List.of(SHARED_ENTITY_ID), 2, 10));
        assertTrue(store.expandEntityIds(
                        allowedOnly, List.of(SECRET_NEIGHBOR_ID), 2, 10)
                .isEmpty());
        assertEquals(
                1L,
                store.loadVisibleEntityDegrees(allowedOnly, List.of(SHARED_ENTITY_ID))
                        .get(SHARED_ENTITY_ID));
        assertEquals(
                2L,
                store.loadVisibleEntityDegrees(allPrimaryEvidence, List.of(SHARED_ENTITY_ID))
                        .get(SHARED_ENTITY_ID));
        assertEquals(
                1.0,
                store.loadVisibleRelationWeights(
                                allowedOnly, List.of(PUBLIC_RELATION_ID))
                        .get(PUBLIC_RELATION_ID));

        assertEquals(
                List.of(SHARED_ENTITY_ID),
                store.searchEntitiesByVector(
                                allowedOnly,
                                primaryOrganization.embeddingProfileId(),
                                3,
                                vector(1.0f, 0.0f, 0.0f),
                                0.1,
                                1)
                        .stream()
                        .map(result -> result.value().id())
                        .toList());
        assertEquals(
                List.of(PUBLIC_RELATION_ID),
                store.searchRelationsByVector(
                                allowedOnly,
                                primaryOrganization.embeddingProfileId(),
                                3,
                                vector(1.0f, 0.0f, 0.0f),
                                0.1,
                                10)
                        .stream()
                        .map(result -> result.value().id())
                        .toList());
    }

    @Test
    void tenantIdIsRequiredEvenWhenCanonicalIdsAndVectorsMatch() {
        AuthorizedEvidenceScope primaryScope = scope(
                primaryOrganization,
                Set.of(allowedAsset.knowledgeAssetId(), otherTenantAsset.knowledgeAssetId()));

        assertEquals(
                1,
                store.loadEntityContributions(primaryScope, List.of(SHARED_ENTITY_ID)).size());
        assertEquals(
                1,
                store.searchEntitiesByVector(
                                primaryScope,
                                primaryOrganization.embeddingProfileId(),
                                3,
                                vector(1.0f, 0.0f, 0.0f),
                                0.1,
                                10)
                        .size());
    }

    @Test
    void vectorSearchRejectsAnArchivedAssetEvenWhenItsProjectionHeadRemains() {
        AssetFixture archivedAsset = seedAsset(primaryOrganization, "archived-vector");
        store.replaceRevision(
                publicProjection(archivedAsset, 1, archivedAsset.chunkId()));
        store.replaceRevisionEmbeddings(publicEmbeddings(archivedAsset, 1));
        AuthorizedEvidenceScope archivedScope = scope(
                primaryOrganization, Set.of(archivedAsset.knowledgeAssetId()));

        assertEquals(
                1,
                store.searchEntitiesByVector(
                                archivedScope,
                                primaryOrganization.embeddingProfileId(),
                                3,
                                vector(1.0f, 0.0f, 0.0f),
                                0.1,
                                10)
                        .size());
        assertEquals(
                1,
                store.searchRelationsByVector(
                                archivedScope,
                                primaryOrganization.embeddingProfileId(),
                                3,
                                vector(1.0f, 0.0f, 0.0f),
                                0.1,
                                10)
                        .size());

        jdbc.update(
                """
                UPDATE knowledge_assets
                SET archived_at = now(), current_version_id = NULL
                WHERE id = ?
                """,
                archivedAsset.knowledgeAssetId());

        assertTrue(store.searchEntitiesByVector(
                        archivedScope,
                        primaryOrganization.embeddingProfileId(),
                        3,
                        vector(1.0f, 0.0f, 0.0f),
                        0.1,
                        10)
                .isEmpty());
        assertTrue(store.searchRelationsByVector(
                        archivedScope,
                        primaryOrganization.embeddingProfileId(),
                        3,
                        vector(1.0f, 0.0f, 0.0f),
                        0.1,
                        10)
                .isEmpty());
    }

    @Test
    void provisionsReplaceableVectorIndexStrategiesAndRejectsUnsupportedDimensions() {
        PostgresGraphVectorIndexManager indexManager =
                new PostgresGraphVectorIndexManager(jdbc);
        PostgresGraphStoreOptions halfVector = new PostgresGraphStoreOptions(
                ApacheAgeMode.DISABLED,
                200,
                4L * 1024 * 1024,
                PostgresVectorIndexStrategy.HNSW_HALFVEC,
                Set.of(3072),
                16,
                64,
                100,
                "");

        indexManager.ensureConfiguredIndexes(halfVector);

        assertEquals(
                2,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_indexes
                        WHERE indexname IN (
                            'idx_entity_embeddings_3072_hnsw_halfvec_cosine',
                            'idx_relation_embeddings_3072_hnsw_halfvec_cosine'
                        )
                        """, Integer.class));
        PostgresGraphProjectionStore halfVectorStore = new PostgresGraphProjectionStore(
                new NamedParameterJdbcTemplate(jdbc.getDataSource()),
                new DataSourceTransactionManager(jdbc.getDataSource()),
                CLOCK,
                halfVector);
        assertEquals(
                List.of(SHARED_ENTITY_ID),
                halfVectorStore.searchEntitiesByVector(
                                scope(
                                        primaryOrganization,
                                        Set.of(allowedAsset.knowledgeAssetId())),
                                primaryOrganization.embeddingProfileId(),
                                3,
                                vector(1.0f, 0.0f, 0.0f),
                                0.1,
                                1)
                        .stream()
                        .map(result -> result.value().id())
                        .toList());

        PostgresGraphStoreOptions invalidFullVector = new PostgresGraphStoreOptions(
                ApacheAgeMode.DISABLED,
                200,
                4L * 1024 * 1024,
                PostgresVectorIndexStrategy.HNSW,
                Set.of(3072),
                16,
                64,
                100,
                "");
        assertThrows(
                IllegalArgumentException.class,
                () -> indexManager.ensureConfiguredIndexes(invalidFullVector));

        PostgresGraphStoreOptions exact = new PostgresGraphStoreOptions(
                ApacheAgeMode.DISABLED,
                200,
                4L * 1024 * 1024,
                PostgresVectorIndexStrategy.EXACT,
                Set.of(3072),
                16,
                64,
                100,
                "");
        indexManager.ensureConfiguredIndexes(exact);
        assertEquals(
                0,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_indexes
                        WHERE indexname IN (
                            'idx_entity_embeddings_3072_hnsw_halfvec_cosine',
                            'idx_relation_embeddings_3072_hnsw_halfvec_cosine'
                        )
                        """, Integer.class));
    }

    @Test
    void replacementIsAtomicAndProjectionGenerationCannotMoveBackwards() {
        UUID generationTwoChunk = seedAdditionalChunk(replacementAsset, 1, 2);
        GraphRevisionContributions generationTwo =
                publicProjection(replacementAsset, 2, generationTwoChunk);
        store.replaceRevision(generationTwo);

        EntityContribution conflictingIdentity = new EntityContribution(
                id("conflicting-replacement"),
                new CanonicalEntity(SHARED_ENTITY_ID, "Different identity"),
                "PRODUCT",
                "Must be rejected before replacing the current generation.",
                provenance(
                        replacementAsset,
                        generationTwoChunk,
                        2,
                        0.8));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.replaceRevision(new GraphRevisionContributions(
                        replacementAsset.organizationId(),
                        replacementAsset.knowledgeAssetId(),
                        replacementAsset.sourceRevisionId(),
                        2,
                        List.of(conflictingIdentity),
                        List.of())));

        AuthorizedEvidenceScope scope =
                scope(primaryOrganization, Set.of(replacementAsset.knowledgeAssetId()));
        assertEquals(
                "Approved secure retrieval evidence.",
                store.loadEntityContributions(scope, List.of(SHARED_ENTITY_ID))
                        .getFirst()
                        .description());
        assertThrows(
                IllegalArgumentException.class,
                () -> store.replaceRevision(
                        publicProjection(
                                replacementAsset, 1, replacementAsset.chunkId())));
    }

    @Test
    void embeddingBatchMustMatchCurrentContributionGeneration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.replaceRevisionEmbeddings(publicEmbeddings(allowedAsset, 999)));
    }

    @Test
    void publishesContributionsAndEmbeddingsAtomically() {
        UUID generationTwoChunk = seedAdditionalChunk(atomicPublicationAsset, 1, 2);
        GraphRevisionContributions generationTwo =
                publicProjection(atomicPublicationAsset, 2, generationTwoChunk);
        store.publish(new GraphRevisionProjection(
                generationTwo, publicEmbeddings(atomicPublicationAsset, 2)));
        GraphRevisionProjection replay = new GraphRevisionProjection(
                generationTwo, publicEmbeddings(atomicPublicationAsset, 2));
        store.publish(replay);
        assertThrows(
                com.orgmemory.graphrag.storage.ProjectionPublicationStore
                        .PublicationConflictException.class,
                () -> store.publish(new GraphRevisionProjection(
                        generationTwo,
                        embeddingsWithDifferentValues(
                                atomicPublicationAsset, generationTwo))));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        """
                        SELECT projection_generation
                        FROM graph_projection_heads
                        WHERE organization_id = ?
                          AND source_revision_id = ?
                        """,
                        Long.class,
                        atomicPublicationAsset.organizationId(),
                        atomicPublicationAsset.sourceRevisionId()));

        UUID generationThreeChunk = seedAdditionalChunk(atomicPublicationAsset, 2, 3);
        GraphRevisionContributions generationThree =
                publicProjection(atomicPublicationAsset, 3, generationThreeChunk);
        GraphRevisionEmbeddings invalidEmbeddings = new GraphRevisionEmbeddings(
                atomicPublicationAsset.organizationId(),
                atomicPublicationAsset.knowledgeAssetId(),
                atomicPublicationAsset.sourceRevisionId(),
                3,
                UUID.randomUUID(),
                3,
                generationThree.entities().stream()
                        .map(contribution -> new ContributionEmbedding(
                                contribution.id(), vector(1.0f, 0.0f, 0.0f)))
                        .toList(),
                generationThree.relations().stream()
                        .map(contribution -> new ContributionEmbedding(
                                contribution.id(), vector(1.0f, 0.0f, 0.0f)))
                        .toList());

        assertThrows(
                RuntimeException.class,
                () -> store.publish(new GraphRevisionProjection(
                        generationThree, invalidEmbeddings)));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        """
                        SELECT projection_generation
                        FROM graph_projection_heads
                        WHERE organization_id = ?
                          AND source_revision_id = ?
                        """,
                        Long.class,
                        atomicPublicationAsset.organizationId(),
                        atomicPublicationAsset.sourceRevisionId()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM graph_entity_contributions
                        WHERE organization_id = ?
                          AND source_revision_id = ?
                          AND projection_generation = 3
                        """,
                        Integer.class,
                        atomicPublicationAsset.organizationId(),
                        atomicPublicationAsset.sourceRevisionId()));
    }

    @Test
    void exactCachesAreTenantScopedGenerationBoundAndInvalidatable() {
        ProjectionNamespace namespace = namespace(primaryOrganization);
        ModelInvocationCache.Key modelKey = new ModelInvocationCache.Key(
                namespace,
                "KEYWORDS",
                "a".repeat(64),
                "route-v1",
                "profile-v1");
        ModelInvocationCache.Entry modelEntry = new ModelInvocationCache.Entry(
                "application/json",
                "{\"high\":[]}",
                NOW,
                NOW.plusSeconds(60));
        cacheStore.put(modelKey, modelEntry);
        assertEquals(
                modelEntry,
                cacheStore.get(modelKey, NOW.plusSeconds(1)).orElseThrow());

        AuthorizedEvidenceScope scope =
                scope(primaryOrganization, Set.of(allowedAsset.knowledgeAssetId()));
        ProjectionSnapshot generationOne = new ProjectionSnapshot(
                id("cache-batch-one"),
                namespace,
                1,
                "cache-manifest-one",
                Set.of(ProjectionKind.CONTENT, ProjectionKind.VECTOR),
                NOW);
        RetrievalResultCache.Key retrievalKey = RetrievalResultCache.key(
                scope,
                generationOne,
                "b".repeat(64),
                "MIX",
                "route-v1");
        RetrievalResultCache.Entry retrievalEntry =
                new RetrievalResultCache.Entry(
                        "application/json",
                        "{\"answer\":\"allowed\"}",
                        List.of(new EvidenceReference(
                                allowedAsset.organizationId(),
                                allowedAsset.knowledgeAssetId(),
                                allowedAsset.sourceRevisionId(),
                                allowedAsset.chunkId(),
                                allowedAsset.aclSnapshotId(),
                                1)),
                        NOW,
                        NOW.plusSeconds(60));
        cacheStore.put(retrievalKey, retrievalEntry);
        assertEquals(
                retrievalEntry,
                cacheStore
                        .get(retrievalKey, NOW.plusSeconds(1))
                        .orElseThrow());

        RetrievalResultCache.Key newGeneration = RetrievalResultCache.key(
                scope,
                new ProjectionSnapshot(
                        id("cache-batch-two"),
                        namespace,
                        2,
                        "cache-manifest-two",
                        Set.of(ProjectionKind.CONTENT, ProjectionKind.VECTOR),
                        NOW.plusSeconds(2)),
                "b".repeat(64),
                "MIX",
                "route-v1");
        assertTrue(cacheStore.get(newGeneration, NOW.plusSeconds(2)).isEmpty());

        cacheStore.invalidateAll(namespace);
        assertTrue(cacheStore.get(modelKey, NOW.plusSeconds(2)).isEmpty());
        assertTrue(cacheStore.get(retrievalKey, NOW.plusSeconds(2)).isEmpty());
    }

    @Test
    void curationIsAppendOnlyIdempotentAndFilteredByVisibleEvidence() {
        ProjectionNamespace namespace = namespace(primaryOrganization);
        CurationProvenance provenance = new CurationProvenance(
                id("curator"),
                "model-v1",
                1,
                NOW,
                "merge duplicate identities");
        GraphCurationRecord alias = new GraphCurationRecord.IdentityAlias(
                id("alias-record"),
                namespace,
                GraphIdentityRef.entity(PUBLIC_NEIGHBOR_ID),
                GraphIdentityRef.entity(SHARED_ENTITY_ID),
                provenance);
        assertEquals(alias, curationStore.append("alias-idempotency", alias));
        assertEquals(alias, curationStore.append("alias-idempotency", alias));

        AuthorizedEvidenceScope allowedScope =
                scope(primaryOrganization, Set.of(allowedAsset.knowledgeAssetId()));
        assertTrue(curationStore.active(allowedScope, namespace).contains(alias));
        assertTrue(curationStore
                .active(
                        scope(
                                otherOrganization,
                                Set.of(otherTenantAsset.knowledgeAssetId())),
                        namespace(otherOrganization))
                .isEmpty());

        curationStore.deactivate(
                namespace,
                alias.id(),
                new CurationProvenance(
                        id("curator"),
                        "model-v1",
                        1,
                        NOW.plusSeconds(1),
                        "restore identities"));
        assertTrue(curationStore.active(allowedScope, namespace).isEmpty());
    }

    @Test
    void exportAggregatesOnlyAuthorizedEvidenceAndCarriesProvenance() {
        AuthorizedEvidenceScope allowedScope =
                scope(primaryOrganization, Set.of(allowedAsset.knowledgeAssetId()));

        GraphExportDocument export =
                exportReader.read(allowedScope, namespace(primaryOrganization));

        assertTrue(export.entities().stream()
                .flatMap(row -> row.evidence().stream())
                .allMatch(evidence -> evidence
                        .knowledgeAssetId()
                        .equals(allowedAsset.knowledgeAssetId())));
        assertTrue(export.relations().stream()
                .flatMap(row -> row.evidence().stream())
                .allMatch(evidence -> evidence
                        .knowledgeAssetId()
                        .equals(allowedAsset.knowledgeAssetId())));
        assertTrue(export.entities().stream()
                .noneMatch(row -> row.description().contains("restricted")));
        assertTrue(export.relations().stream()
                .noneMatch(row -> row.description().contains("restricted")));
    }

    @Test
    void currentAclRevocationHidesProjectionCurationAndExport() {
        AssetFixture fixture = seedAsset(primaryOrganization, "acl-revocation");
        UUID entityId = id("acl-revocation-entity");
        store.replaceRevision(singleEntityProjection(
                fixture,
                entityId,
                "Evidence visible before the current source ACL is revoked."));
        AuthorizedEvidenceScope authorizedScope =
                scope(primaryOrganization, Set.of(fixture.knowledgeAssetId()));
        ProjectionNamespace namespace = namespace(primaryOrganization);
        GraphCurationRecord suppression = new GraphCurationRecord.IdentitySuppression(
                id("acl-revocation-suppression"),
                namespace,
                GraphIdentityRef.entity(entityId),
                new CurationProvenance(
                        primaryOrganization.userId(),
                        "model-v1",
                        1,
                        NOW,
                        "suppress duplicate test identity"));
        assertEquals(
                1,
                store.loadEntityContributions(authorizedScope, List.of(entityId))
                        .size());
        assertEquals(1, exportReader.read(authorizedScope, namespace).entities().size());
        curationStore.append("acl-revocation-suppression", suppression);
        assertTrue(curationStore.active(authorizedScope, namespace).contains(suppression));

        revokeCurrentAcl(fixture);

        assertTrue(store.loadEntityContributions(authorizedScope, List.of(entityId))
                .isEmpty());
        assertTrue(curationStore.active(authorizedScope, namespace).isEmpty());
        assertTrue(exportReader.read(authorizedScope, namespace).entities().isEmpty());
    }

    @Test
    void removingOneRevisionPreservesCanonicalIdentityBackedByAnotherRevision() {
        AssetFixture first = seedAsset(primaryOrganization, "shared-delete-first");
        AssetFixture second = seedAsset(primaryOrganization, "shared-delete-second");
        UUID entityId = id("shared-delete-entity");
        store.replaceRevision(
                singleEntityProjection(first, entityId, "First supporting evidence."));
        store.replaceRevision(
                singleEntityProjection(second, entityId, "Second supporting evidence."));
        AuthorizedEvidenceScope authorizedScope = scope(
                primaryOrganization,
                Set.of(first.knowledgeAssetId(), second.knowledgeAssetId()));

        assertEquals(
                Set.of("First supporting evidence.", "Second supporting evidence."),
                store.loadEntityContributions(authorizedScope, List.of(entityId))
                        .stream()
                        .map(EntityContribution::description)
                        .collect(java.util.stream.Collectors.toSet()));

        store.removeRevision(first.organizationId(), first.sourceRevisionId());

        assertEquals(
                List.of("Second supporting evidence."),
                store.loadEntityContributions(authorizedScope, List.of(entityId))
                        .stream()
                        .map(EntityContribution::description)
                        .toList());
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM graph_entities
                        WHERE organization_id = ?
                          AND id = ?
                        """,
                        Integer.class,
                        first.organizationId(),
                        entityId));
    }

    private static GraphRevisionContributions singleEntityProjection(
            AssetFixture fixture, UUID entityId, String description) {
        return new GraphRevisionContributions(
                fixture.organizationId(),
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                1,
                List.of(entityContribution(
                        fixture.key() + "-single-entity",
                        new CanonicalEntity(entityId, "Entity " + entityId),
                        description,
                        fixture,
                        fixture.chunkId(),
                        1,
                        1.0)),
                List.of());
    }

    private static GraphRevisionEmbeddings embeddingsWithDifferentValues(
            AssetFixture fixture, GraphRevisionContributions contributions) {
        return new GraphRevisionEmbeddings(
                fixture.organizationId(),
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                contributions.projectionGeneration(),
                fixture.embeddingProfileId(),
                3,
                contributions.entities().stream()
                        .map(contribution -> new ContributionEmbedding(
                                contribution.id(),
                                vector(0.0f, 1.0f, 0.0f)))
                        .toList(),
                contributions.relations().stream()
                        .map(contribution -> new ContributionEmbedding(
                                contribution.id(),
                                vector(0.0f, 1.0f, 0.0f)))
                        .toList());
    }

    private static ProjectionNamespace namespace(
            OrganizationFixture organization) {
        return new ProjectionNamespace(
                organization.organizationId(), "default", "knowledge");
    }

    private static GraphRevisionContributions publicProjection(
            AssetFixture fixture,
            long generation,
            UUID chunkId) {
        CanonicalEntity shared = new CanonicalEntity(
                SHARED_ENTITY_ID, "OrgMemory");
        CanonicalEntity neighbor = new CanonicalEntity(
                PUBLIC_NEIGHBOR_ID, "Secure Search");
        CanonicalRelation relation = new CanonicalRelation(
                PUBLIC_RELATION_ID,
                SHARED_ENTITY_ID,
                PUBLIC_NEIGHBOR_ID,
                RelationOrientation.DIRECTED);
        return new GraphRevisionContributions(
                fixture.organizationId(),
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                generation,
                List.of(
                        entityContribution(
                                "public-shared-" + fixture.key() + "-" + generation,
                                shared,
                                "Approved secure retrieval evidence.",
                                fixture,
                                chunkId,
                                generation,
                                0.9),
                        entityContribution(
                                "public-neighbor-" + fixture.key() + "-" + generation,
                                neighbor,
                                "Secure Search filters evidence before ranking.",
                                fixture,
                                chunkId,
                                generation,
                                0.8)),
                List.of(relationContribution(
                        "public-relation-" + fixture.key() + "-" + generation,
                        relation,
                        List.of("security", "retrieval"),
                        "OrgMemory builds Secure Search.",
                        fixture,
                        chunkId,
                        generation,
                        0.7)));
    }

    private static GraphRevisionContributions restrictedProjection(AssetFixture fixture) {
        CanonicalEntity shared = new CanonicalEntity(
                SHARED_ENTITY_ID, "OrgMemory");
        CanonicalEntity neighbor = new CanonicalEntity(
                SECRET_NEIGHBOR_ID, "Project Nightfall");
        CanonicalRelation relation = new CanonicalRelation(
                SECRET_RELATION_ID,
                SHARED_ENTITY_ID,
                SECRET_NEIGHBOR_ID,
                RelationOrientation.DIRECTED);
        return new GraphRevisionContributions(
                fixture.organizationId(),
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                1,
                List.of(
                        entityContribution(
                                "restricted-shared",
                                shared,
                                "Confidential Nightfall acquisition evidence.",
                                fixture,
                                fixture.chunkId(),
                                1,
                                1.0),
                        entityContribution(
                                "restricted-neighbor",
                                neighbor,
                                "Project Nightfall is an acquisition target.",
                                fixture,
                                fixture.chunkId(),
                                1,
                                1.0)),
                List.of(relationContribution(
                        "restricted-relation",
                        relation,
                        List.of("nightfall", "acquisition"),
                        "OrgMemory acquires Project Nightfall.",
                        fixture,
                        fixture.chunkId(),
                        1,
                        1.0)));
    }

    private static GraphRevisionEmbeddings publicEmbeddings(
            AssetFixture fixture,
            long generation) {
        return new GraphRevisionEmbeddings(
                fixture.organizationId(),
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                generation,
                fixture.embeddingProfileId(),
                3,
                List.of(
                        new ContributionEmbedding(
                                id("public-shared-" + fixture.key() + "-" + generation),
                                vector(1.0f, 0.0f, 0.0f)),
                        new ContributionEmbedding(
                                id("public-neighbor-" + fixture.key() + "-" + generation),
                                vector(0.0f, 1.0f, 0.0f))),
                List.of(new ContributionEmbedding(
                        id("public-relation-" + fixture.key() + "-" + generation),
                        vector(1.0f, 0.0f, 0.0f))));
    }

    private static GraphRevisionEmbeddings restrictedEmbeddings(AssetFixture fixture) {
        return new GraphRevisionEmbeddings(
                fixture.organizationId(),
                fixture.knowledgeAssetId(),
                fixture.sourceRevisionId(),
                1,
                fixture.embeddingProfileId(),
                3,
                List.of(
                        new ContributionEmbedding(
                                id("restricted-shared"),
                                vector(1.0f, 0.0f, 0.0f)),
                        new ContributionEmbedding(
                                id("restricted-neighbor"),
                                vector(1.0f, 0.0f, 0.0f))),
                List.of(new ContributionEmbedding(
                        id("restricted-relation"),
                        vector(1.0f, 0.0f, 0.0f))));
    }

    private static EntityContribution entityContribution(
            String key,
            CanonicalEntity entity,
            String description,
            AssetFixture fixture,
            UUID chunkId,
            long generation,
            double confidence) {
        return new EntityContribution(
                id(key),
                entity,
                entityType(entity),
                description,
                provenance(fixture, chunkId, generation, confidence));
    }

    private static String entityType(CanonicalEntity entity) {
        if (SHARED_ENTITY_ID.equals(entity.id())) {
            return "PRODUCT";
        }
        if (PUBLIC_NEIGHBOR_ID.equals(entity.id())) {
            return "CAPABILITY";
        }
        return "INITIATIVE";
    }

    private static RelationContribution relationContribution(
            String key,
            CanonicalRelation relation,
            List<String> keywords,
            String description,
            AssetFixture fixture,
            UUID chunkId,
            long generation,
            double confidence) {
        return new RelationContribution(
                id(key),
                relation,
                PUBLIC_RELATION_ID.equals(relation.id()) ? "BUILDS" : "ACQUIRES",
                keywords,
                description,
                1.0,
                provenance(fixture, chunkId, generation, confidence));
    }

    private static EvidenceProvenance provenance(
            AssetFixture fixture,
            UUID chunkId,
            long generation,
            double confidence) {
        return new EvidenceProvenance(
                new EvidenceReference(
                        fixture.organizationId(),
                        fixture.knowledgeAssetId(),
                        fixture.sourceRevisionId(),
                        chunkId,
                        fixture.aclSnapshotId(),
                        1),
                generation,
                "openai",
                "gpt-5.6-sol",
                "orgmemory-graph-extraction-v1",
                confidence,
                NOW);
    }

    private static AuthorizedEvidenceScope scope(
            OrganizationFixture organization,
            Set<UUID> assets) {
        return new AuthorizedEvidenceScope(
                organization.organizationId(),
                organization.userId(),
                organization.departmentId(),
                false,
                assets,
                "model-v1",
                1,
                NOW);
    }

    private static OrganizationFixture seedOrganization(String key) {
        UUID organizationId = id(key + "-organization");
        UUID departmentId = id(key + "-department");
        UUID userId = id(key + "-user");
        UUID profileId = id(key + "-embedding-profile");
        UUID spaceId = id(key + "-knowledge-space");
        jdbc.update("""
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                """, organizationId, key);
        jdbc.update("""
                INSERT INTO departments (
                    id, organization_id, name, created_at, updated_at, version)
                VALUES (?, ?, ?, now(), now(), 0)
                """, departmentId, organizationId, key);
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'CONTRIBUTOR', true, now(), now(), 0)
                """,
                userId,
                organizationId,
                departmentId,
                key,
                key + "@example.test");
        jdbc.update("""
                INSERT INTO embedding_profiles (
                    id, organization_id, profile_key, provider, model, dimensions,
                    distance_metric, created_at)
                VALUES (?, ?, ?, 'test', 'test-3', 3, 'COSINE', now())
                """, profileId, organizationId, key + "/test-3");
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, true, now(), now(), 0)
                """, spaceId, organizationId, departmentId, key, key);
        return new OrganizationFixture(
                organizationId, departmentId, userId, profileId, spaceId);
    }

    private static AssetFixture seedAsset(
            OrganizationFixture organization,
            String key) {
        UUID blobId = id(key + "-blob-" + organization.organizationId());
        UUID rawId = id(key + "-raw-" + organization.organizationId());
        UUID snapshotId = id(key + "-snapshot-" + organization.organizationId());
        UUID aclHeadId = id(key + "-acl-head-" + organization.organizationId());
        UUID normalizedId = id(key + "-normalized-" + organization.organizationId());
        UUID assetId = id(key + "-asset-" + organization.organizationId());
        UUID assetVersionId = id(key + "-asset-version-" + organization.organizationId());
        UUID publicationId = id(key + "-publication-" + organization.organizationId());
        UUID sourceObjectId = id(key + "-object-" + organization.organizationId());
        UUID revisionId = id(key + "-revision-" + organization.organizationId());
        UUID chunkId = id(key + "-chunk-" + organization.organizationId());
        String content = "content-" + key;
        String sha = sha256(content);
        jdbc.update("""
                INSERT INTO evidence_blobs (
                    id, organization_id, object_key, media_type, content_length,
                    content_sha256, scan_status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'text/plain', ?, ?, 'BASIC_VALIDATED', now(), now(), 0)
                """,
                blobId,
                organization.organizationId(),
                "graph-tests/" + organization.organizationId() + "/" + key,
                content.length(),
                sha);
        jdbc.update("""
                INSERT INTO raw_source_objects (
                    id, organization_id, source_system, source_connection_key,
                    external_object_id, source_version, object_type, title, raw_content,
                    payload_sha256, classification, declared_access, status,
                    created_at, updated_at, version)
                VALUES (?, ?, 'upload', 'graph-tests', ?, '1', 'document', ?, ?, ?,
                    'INTERNAL', 'ALL_EMPLOYEES', 'NORMALIZED', now(), now(), 0)
                """,
                rawId,
                organization.organizationId(),
                key,
                key,
                content,
                sha);
        jdbc.update("""
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation,
                    capture_status, default_gate, acl_sha256, captured_at, valid_until)
                VALUES (?, ?, ?, 1, 'COMPLETE', 'ALLOW', ?, ?, ?)
                """,
                snapshotId,
                organization.organizationId(),
                rawId,
                sha,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW.plusSeconds(3600)));
        jdbc.update("""
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count,
                    entries_sha256, sealed_at)
                VALUES (?, ?, 0, ?, now())
                """, snapshotId, organization.organizationId(), sha);
        jdbc.update("""
                INSERT INTO source_acl_heads (
                    id, organization_id, source_system, source_connection_key,
                    external_object_id, current_raw_source_object_id,
                    current_snapshot_id, acl_generation, created_at, updated_at, version)
                VALUES (?, ?, 'upload', 'graph-tests', ?, ?, ?, 1, now(), now(), 0)
                """,
                aclHeadId,
                organization.organizationId(),
                key,
                rawId,
                snapshotId);
        jdbc.update("""
                INSERT INTO normalized_records (
                    id, organization_id, raw_source_object_id, source_acl_snapshot_id,
                    normalizer_version, title, normalized_content, language,
                    classification, declared_access, content_sha256, status,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'test-v1', ?, ?, 'en', 'INTERNAL',
                    'ALL_EMPLOYEES', ?, 'PROMOTED', now(), now(), 0)
                """,
                normalizedId,
                organization.organizationId(),
                rawId,
                snapshotId,
                key,
                content,
                sha);
        jdbc.update("""
                INSERT INTO source_objects (
                    id, organization_id, created_by_user_id, knowledge_space_id,
                    acl_authority, source_system, source_connection_key, external_object_id, title,
                    classification, declared_access, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ORGMEMORY', 'upload', 'graph-tests', ?, ?, 'INTERNAL',
                    'ALL_EMPLOYEES', 'ACTIVE', now(), now(), 0)
                """,
                sourceObjectId,
                organization.organizationId(),
                organization.userId(),
                organization.knowledgeSpaceId(),
                key,
                key);
        jdbc.update("""
                INSERT INTO knowledge_assets (
                    id, organization_id, knowledge_space_id, source_object_id,
                    current_version_id, archived_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, NULL, NULL, now(), now(), 0)
                """,
                assetId,
                organization.organizationId(),
                organization.knowledgeSpaceId(),
                sourceObjectId);
        jdbc.update("""
                INSERT INTO source_revisions (
                    id, organization_id, source_object_id, knowledge_space_id,
                    evidence_blob_id, revision_number, file_name, media_type,
                    content_length, content_sha256, classification, declared_access,
                    created_by_user_id, status, pipeline_version, parser_version,
                    chunker_version, embedding_profile_id, embedding_dimensions,
                    raw_source_object_id, normalized_record_id, knowledge_asset_id,
                    processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 1, ?, 'text/plain', ?, ?, 'INTERNAL',
                    'ALL_EMPLOYEES', ?, 'READY', 'test-v1', 'test-v1', 'test-v1',
                    ?, 3, ?, ?, ?, now(), now(), now(), 0)
                """,
                revisionId,
                organization.organizationId(),
                sourceObjectId,
                organization.knowledgeSpaceId(),
                blobId,
                key + ".txt",
                content.length(),
                sha,
                organization.userId(),
                organization.embeddingProfileId(),
                rawId,
                normalizedId,
                assetId);
        jdbc.update("""
                INSERT INTO knowledge_asset_versions (
                    id, organization_id, raw_source_object_id, normalized_record_id,
                    source_acl_snapshot_id, knowledge_space_id, title, content, language,
                    classification, declared_access, content_sha256, orgmemory_gate,
                    status, activated_at, created_at, updated_at, version,
                    knowledge_asset_id, version_number, source_revision_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'en', 'INTERNAL', 'ALL_EMPLOYEES',
                    ?, 'ALLOW', 'ACTIVE', now(), now(), now(), 0, ?, 1, ?)
                """,
                assetVersionId,
                organization.organizationId(),
                rawId,
                normalizedId,
                snapshotId,
                organization.knowledgeSpaceId(),
                key,
                content,
                sha,
                assetId,
                revisionId);
        jdbc.update("""
                UPDATE source_revisions
                SET knowledge_asset_version_id = ?
                WHERE id = ?
                """, assetVersionId, revisionId);
        jdbc.update("""
                UPDATE source_objects
                SET current_revision_id = ?, latest_revision_id = ?, updated_at = now()
                WHERE id = ?
                """, revisionId, revisionId, sourceObjectId);
        jdbc.update("""
                UPDATE knowledge_assets
                SET current_version_id = ?, updated_at = now()
                WHERE id = ?
                """, assetVersionId, assetId);
        jdbc.update("""
                INSERT INTO knowledge_chunks (
                    id, organization_id, source_object_id, source_revision_id,
                    knowledge_asset_id, knowledge_asset_version_id,
                    chunk_index, content, content_sha256, embedding,
                    embedding_profile_id, embedding_dimensions, pipeline_version,
                    projection_generation, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, '[1,0,0]'::vector, ?, 3,
                    'test-v1', 1, true, now())
                """,
                chunkId,
                organization.organizationId(),
                sourceObjectId,
                revisionId,
                assetId,
                assetVersionId,
                content,
                sha,
                organization.embeddingProfileId());
        jdbc.update("""
                INSERT INTO knowledge_asset_publication_outbox (
                    id, organization_id, source_revision_id, source_object_id,
                    knowledge_asset_id, owner_user_id, projection_generation,
                    embedding_profile_id, embedding_dimensions, pipeline_version,
                    status, attempt_count, authorization_model_id, applied_at,
                    created_at, updated_at, version, knowledge_space_id,
                    knowledge_asset_version_id)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, 3, 'test-v1',
                    'APPLIED', 1, 'model-v1', now(), now(), now(), 0, ?, ?)
                """,
                publicationId,
                organization.organizationId(),
                revisionId,
                sourceObjectId,
                assetId,
                organization.userId(),
                organization.embeddingProfileId(),
                organization.knowledgeSpaceId(),
                assetVersionId);
        return new AssetFixture(
                key,
                organization.organizationId(),
                organization.embeddingProfileId(),
                rawId,
                sourceObjectId,
                revisionId,
                assetId,
                assetVersionId,
                snapshotId,
                chunkId);
    }

    private static UUID seedAdditionalChunk(
            AssetFixture fixture,
            int chunkIndex,
            long projectionGeneration) {
        UUID chunkId = id(fixture.key() + "-chunk-" + projectionGeneration);
        String content = fixture.key() + "-generation-" + projectionGeneration;
        jdbc.update("""
                INSERT INTO knowledge_chunks (
                    id, organization_id, source_object_id, source_revision_id,
                    knowledge_asset_id, knowledge_asset_version_id,
                    chunk_index, content, content_sha256, embedding,
                    embedding_profile_id, embedding_dimensions, pipeline_version,
                    projection_generation, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '[1,0,0]'::vector, ?, 3,
                    'test-v1', ?, true, now())
                """,
                chunkId,
                fixture.organizationId(),
                fixture.sourceObjectId(),
                fixture.sourceRevisionId(),
                fixture.knowledgeAssetId(),
                fixture.knowledgeAssetVersionId(),
                chunkIndex,
                content,
                sha256(content),
                fixture.embeddingProfileId(),
                projectionGeneration);
        jdbc.update("""
                UPDATE knowledge_asset_publication_outbox
                SET projection_generation = ?, updated_at = now(), version = version + 1
                WHERE organization_id = ?
                  AND knowledge_asset_version_id = ?
                """,
                projectionGeneration,
                fixture.organizationId(),
                fixture.knowledgeAssetVersionId());
        return chunkId;
    }

    private static void revokeCurrentAcl(AssetFixture fixture) {
        UUID revokedSnapshotId =
                id(fixture.key() + "-revoked-snapshot-" + fixture.organizationId());
        String snapshotHash = sha256(fixture.key() + "-revoked");
        jdbc.update("""
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation,
                    capture_status, default_gate, acl_sha256, captured_at, valid_until)
                VALUES (?, ?, ?, 2, 'COMPLETE', 'DENY', ?, ?, ?)
                """,
                revokedSnapshotId,
                fixture.organizationId(),
                fixture.rawSourceObjectId(),
                snapshotHash,
                java.sql.Timestamp.from(NOW.plusSeconds(1)),
                java.sql.Timestamp.from(NOW.plusSeconds(3600)));
        jdbc.update("""
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count,
                    entries_sha256, sealed_at)
                VALUES (?, ?, 0, ?, now())
                """, revokedSnapshotId, fixture.organizationId(), snapshotHash);
        jdbc.update("""
                UPDATE source_acl_heads
                SET current_snapshot_id = ?, acl_generation = 2,
                    updated_at = now(), version = version + 1
                WHERE organization_id = ?
                  AND current_raw_source_object_id = ?
                """,
                revokedSnapshotId,
                fixture.organizationId(),
                fixture.rawSourceObjectId());
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static FloatVector vector(float... values) {
        return new FloatVector(values);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record OrganizationFixture(
            UUID organizationId,
            UUID departmentId,
            UUID userId,
            UUID embeddingProfileId,
            UUID knowledgeSpaceId) {
    }

    private record AssetFixture(
            String key,
            UUID organizationId,
            UUID embeddingProfileId,
            UUID rawSourceObjectId,
            UUID sourceObjectId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            UUID aclSnapshotId,
            UUID chunkId) {
    }
}
