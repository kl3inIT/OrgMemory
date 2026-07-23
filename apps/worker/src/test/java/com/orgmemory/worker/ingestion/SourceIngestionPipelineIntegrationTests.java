package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.CreateUploadSourceCommand;
import com.orgmemory.core.knowledge.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.SourceUploadService;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.QueryEmbedding;
import com.orgmemory.core.knowledge.KnowledgeRetrievalProperties;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "orgmemory.ingestion.processing.scheduling-enabled=false",
        "orgmemory.authorization.convergence.scheduling-enabled=false",
        "orgmemory.ingestion.processing.embedding-model=text-embedding-3-large",
        "orgmemory.ingestion.processing.embedding-dimensions=1536"
})
@Import({
        SourceIngestionPipelineIntegrationTests.UploadTestConfiguration.class,
        SecureKnowledgeRetrievalService.class
})
@EnableConfigurationProperties(KnowledgeRetrievalProperties.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SourceIngestionPipelineIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SALES_SPACE_ID = UUID.fromString("88888888-8888-4888-8888-888888888802");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, DEPARTMENT_ID, "Linh Nguyen", "linh@example.com");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ObjectStoragePort objects;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    RelationshipTupleWritePort relationshipTuples;

    @MockitoBean
    RelationshipTupleReconciliationPort relationshipTupleReconciliation;

    @MockitoBean
    RelationshipAuthorizationPort entryAuthorization;

    @MockitoBean
    RelationshipAuthorizationSetPort setAuthorization;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    @Autowired
    SourceUploadService uploads;

    @Autowired
    SourceIngestionProcessor processor;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EmbeddingProfileRegistry embeddingProfiles;

    @Autowired
    SecureKnowledgeRetrievalService retrieval;

    /**
     * Every test here calls {@code processNext()} expecting to claim the upload it just made,
     * but the claim query takes the oldest available job across the whole table and will also
     * reclaim a job whose lease has expired. Jobs left behind by earlier tests therefore win the
     * race once the suite runs slowly enough for their leases to lapse. Parking them keeps each
     * test's own upload the only claimable work without touching the rows any assertion reads.
     */
    @BeforeEach
    @SuppressWarnings("SqlResolve")
    void parkJobsLeftByEarlierTests() {
        jdbc.update("""
                UPDATE source_ingestion_jobs
                SET available_at = now() + interval '1 day',
                    lease_until = now() + interval '1 day'
                """);
    }

    @Test
    @SuppressWarnings("SqlResolve")
    void processesUploadedTextIntoVersionedLargeModelVectors() throws Exception {
        byte[] content = "Open the customer request, verify the account, and record the resolution."
                .getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        when(objects.put(any(), any())).thenAnswer(invocation -> {
            ObjectWriteRequest request = invocation.getArgument(0);
            return new StoredObject(request.key(), content.length, "text/plain", sha256, "etag-1", null);
        });
        when(objects.open(any())).thenAnswer(invocation -> {
            var key = (com.orgmemory.core.knowledge.storage.ObjectKey) invocation.getArgument(0);
            return new ObjectContent(
                    new ByteArrayInputStream(content),
                    new StoredObject(key, content.length, "text/plain", sha256, "etag-1", null));
        });
        when(embeddingModel.embed(anyList(), isNull(), any(TokenCountBatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    List<Document> documents = invocation.getArgument(0);
                    return documents.stream().map(ignored -> embedding()).toList();
                });
        when(relationshipTuples.write(any(RelationshipTupleWriteRequest.class)))
                .thenReturn(RelationshipTupleWriteResult.applied("model-1"));
        when(entryAuthorization.check(any())).thenReturn(AuthorizationDecision.allow("model-1"));

        var source = uploads.upload(
                new CreateUploadSourceCommand(
                        ACTOR,
                        "support-resolution.txt",
                        "text/plain",
                        content.length,
                        KnowledgeClassification.CONFIDENTIAL,
                        SALES_SPACE_ID),
                new ByteArrayInputStream(content));

        processor.processNext();

        var revision = jdbc.queryForMap(
                """
                SELECT r.status, r.embedding_profile_id, r.embedding_dimensions,
                       r.raw_source_object_id, r.normalized_record_id, r.knowledge_asset_id,
                       p.profile_key, p.provider, p.model, p.distance_metric
                FROM source_revisions r
                JOIN embedding_profiles p ON p.id = r.embedding_profile_id
                WHERE r.source_object_id = ?
                """,
                source.id());
        assertEquals("READY", revision.get("status"));
        assertNotNull(revision.get("embedding_profile_id"));
        assertEquals("openai/text-embedding-3-large/1536/cosine", revision.get("profile_key"));
        assertEquals("openai", revision.get("provider"));
        assertEquals("text-embedding-3-large", revision.get("model"));
        assertEquals("COSINE", revision.get("distance_metric"));
        assertEquals(1536, revision.get("embedding_dimensions"));
        assertNotNull(revision.get("raw_source_object_id"));
        assertNotNull(revision.get("normalized_record_id"));
        assertNotNull(revision.get("knowledge_asset_id"));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        """
                        SELECT version.status
                        FROM knowledge_assets asset
                        JOIN knowledge_asset_versions version
                          ON version.id = asset.current_version_id
                        WHERE asset.id = ?
                        """,
                        String.class,
                        revision.get("knowledge_asset_id")));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_chunks WHERE source_object_id = ?",
                        Integer.class,
                        source.id()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_chunks WHERE source_object_id = ? AND active",
                        Integer.class,
                        source.id()));
        assertEquals(
                1536,
                jdbc.queryForObject(
                        "SELECT vector_dims(embedding) FROM knowledge_chunks WHERE source_object_id = ?",
                        Integer.class,
                        source.id()));
        assertEquals(
                revision.get("embedding_profile_id"),
                jdbc.queryForObject(
                        "SELECT embedding_profile_id FROM knowledge_chunks WHERE source_object_id = ?",
                        UUID.class,
                        source.id()));
        assertEquals(
                "SUCCEEDED",
                jdbc.queryForObject(
                        "SELECT status FROM source_ingestion_jobs WHERE source_revision_id = (SELECT latest_revision_id FROM source_objects WHERE id = ?)",
                        String.class,
                        source.id()));
        var publication = jdbc.queryForMap(
                """
                        SELECT status, attempt_count, authorization_model_id
                        FROM knowledge_asset_publication_outbox
                        WHERE knowledge_asset_id = ?
                        """,
                revision.get("knowledge_asset_id"));
        assertEquals("APPLIED", publication.get("status"));
        assertEquals(1, publication.get("attempt_count"));
        assertEquals("model-1", publication.get("authorization_model_id"));

        var writeRequest = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(relationshipTuples).write(writeRequest.capture());
        assertEquals(2, writeRequest.getValue().tuples().size());
        assertEquals(
                "knowledge_space:" + SALES_SPACE_ID,
                writeRequest.getValue().tuples().getFirst().user());
        assertEquals("space", writeRequest.getValue().tuples().getFirst().relation());
        assertEquals(
                "knowledge_asset:" + revision.get("knowledge_asset_id"),
                writeRequest.getValue().tuples().getFirst().object());
        assertEquals("user:" + USER_ID, writeRequest.getValue().tuples().get(1).user());
        assertEquals("owner", writeRequest.getValue().tuples().get(1).relation());
        assertEquals(
                "knowledge_asset:" + revision.get("knowledge_asset_id"),
                writeRequest.getValue().tuples().get(1).object());

        UUID assetId = (UUID) revision.get("knowledge_asset_id");
        ResourceRef resource = ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", assetId);
        when(setAuthorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(List.of(resource), "model-1"));
        when(setAuthorization.batchCheck(any())).thenReturn(BatchAuthorizationResult.resolved(
                Map.of(resource, AuthorizationDecision.allow("model-1")),
                "model-1"));
        when(queryEmbeddings.embed(any(), any())).thenReturn(Optional.of(new QueryEmbedding(
                (UUID) revision.get("embedding_profile_id"),
                1536,
                embedding())));

        var permitted = retrieval.search(ACTOR, "customer resolution", 10, "retrieval-allowed");
        assertEquals(1, permitted.evidence().size());
        assertEquals(assetId, permitted.evidence().getFirst().knowledgeAssetId());

        jdbc.update(
                "UPDATE knowledge_asset_publication_outbox SET authorization_model_id = 'model-2' WHERE knowledge_asset_id = ?",
                assetId);
        assertEquals(
                0,
                retrieval.search(ACTOR, "customer resolution", 10, "retrieval-model-mismatch")
                        .evidence()
                        .size());
        jdbc.update(
                "UPDATE knowledge_asset_publication_outbox SET authorization_model_id = 'model-1' WHERE knowledge_asset_id = ?",
                assetId);

        assertEquals(
                1,
                retrieval.search(ACTOR, "semantically related but lexically absent", 10, "retrieval-vector")
                        .evidence()
                        .size());
        when(queryEmbeddings.embed(any(), any())).thenReturn(Optional.of(new QueryEmbedding(
                UUID.randomUUID(),
                1536,
                embedding())));
        assertEquals(
                0,
                retrieval.search(ACTOR, "semantically related but lexically absent", 10, "retrieval-profile-mismatch")
                        .evidence()
                        .size());
        when(queryEmbeddings.embed(any(), any())).thenReturn(Optional.of(new QueryEmbedding(
                (UUID) revision.get("embedding_profile_id"),
                1536,
                embedding())));

        jdbc.update("UPDATE knowledge_chunks SET active = false WHERE knowledge_asset_id = ?", assetId);
        assertEquals(
                0,
                retrieval.search(ACTOR, "customer resolution", 10, "retrieval-inactive-generation")
                        .evidence()
                        .size());
        jdbc.update("UPDATE knowledge_chunks SET active = true WHERE knowledge_asset_id = ?", assetId);

        UUID staleSnapshotId = UUID.randomUUID();
        UUID rawSourceId = (UUID) revision.get("raw_source_object_id");
        jdbc.update(
                """
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation,
                    capture_status, default_gate, acl_sha256, captured_at, valid_until
                ) VALUES (?, ?, ?, 2, 'COMPLETE', 'DENY', repeat('d', 64),
                          now() - interval '2 hours', now() - interval '1 hour')
                """,
                staleSnapshotId,
                ORGANIZATION_ID,
                rawSourceId);
        jdbc.update(
                """
                INSERT INTO source_acl_entries (
                    id, organization_id, source_acl_snapshot_id,
                    principal_type, principal_key, gate, created_at
                ) VALUES (?, ?, ?, 'ORGMEMORY_USER', ?, 'ALLOW', now() - interval '2 hours')
                """,
                UUID.randomUUID(),
                ORGANIZATION_ID,
                staleSnapshotId,
                USER_ID.toString());
        jdbc.update(
                """
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count, entries_sha256, sealed_at
                ) VALUES (?, ?, 1, repeat('e', 64), now() - interval '2 hours')
                """,
                staleSnapshotId,
                ORGANIZATION_ID);
        jdbc.update(
                """
                UPDATE source_acl_heads
                SET current_snapshot_id = ?, acl_generation = 2, updated_at = now(), version = version + 1
                WHERE organization_id = ? AND current_raw_source_object_id = ?
                """,
                staleSnapshotId,
                ORGANIZATION_ID,
                rawSourceId);

        var staleAcl = retrieval.search(ACTOR, "customer resolution", 10, "retrieval-stale-acl");
        assertEquals(0, staleAcl.evidence().size());
    }

    @Test
    @SuppressWarnings("SqlResolve")
    void keepsPublicationPendingWhenAuthorizationProjectionIsUnavailable() throws Exception {
        byte[] content = "Resolve the request but do not publish before authorization converges."
                .getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        when(objects.put(any(), any())).thenAnswer(invocation -> {
            ObjectWriteRequest request = invocation.getArgument(0);
            return new StoredObject(request.key(), content.length, "text/plain", sha256, "etag-2", null);
        });
        when(objects.open(any())).thenAnswer(invocation -> {
            var key = (com.orgmemory.core.knowledge.storage.ObjectKey) invocation.getArgument(0);
            return new ObjectContent(
                    new ByteArrayInputStream(content),
                    new StoredObject(key, content.length, "text/plain", sha256, "etag-2", null));
        });
        when(embeddingModel.embed(anyList(), isNull(), any(TokenCountBatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    List<Document> documents = invocation.getArgument(0);
                    return documents.stream().map(ignored -> embedding()).toList();
                });
        when(relationshipTuples.write(any(RelationshipTupleWriteRequest.class)))
                .thenReturn(RelationshipTupleWriteResult.indeterminate(
                        "OPENFGA_WRITE_UNAVAILABLE", "model-1"));
        when(entryAuthorization.check(any())).thenReturn(AuthorizationDecision.allow("model-1"));

        var source = uploads.upload(
                new CreateUploadSourceCommand(
                        ACTOR,
                        "authorization-pending.txt",
                        "text/plain",
                        content.length,
                        KnowledgeClassification.CONFIDENTIAL,
                        SALES_SPACE_ID),
                new ByteArrayInputStream(content));

        processor.processNext();

        var state = jdbc.queryForMap(
                """
                        SELECT r.status AS revision_status,
                               kav.status AS asset_status,
                               p.status AS publication_status,
                               p.attempt_count,
                               p.last_error_code,
                               bool_and(NOT c.active) AS all_chunks_inactive
                        FROM source_revisions r
                        JOIN knowledge_asset_publication_outbox p ON p.source_revision_id = r.id
                        JOIN knowledge_asset_versions kav ON kav.id = p.knowledge_asset_version_id
                        JOIN knowledge_chunks c
                          ON c.knowledge_asset_version_id = kav.id
                        WHERE r.source_object_id = ?
                        GROUP BY r.status, kav.status, p.status, p.attempt_count, p.last_error_code
                        """,
                source.id());
        assertEquals("RECEIVED", state.get("revision_status"));
        assertEquals("PENDING", state.get("asset_status"));
        assertEquals("PENDING", state.get("publication_status"));
        assertEquals(1, state.get("attempt_count"));
        assertEquals("OPENFGA_WRITE_UNAVAILABLE", state.get("last_error_code"));
        assertEquals(true, state.get("all_chunks_inactive"));
        assertEquals(
                "PENDING",
                jdbc.queryForObject(
                        "SELECT status FROM source_ingestion_jobs WHERE source_revision_id = (SELECT latest_revision_id FROM source_objects WHERE id = ?)",
                        String.class,
                        source.id()));

        when(relationshipTuples.write(any(RelationshipTupleWriteRequest.class)))
                .thenReturn(RelationshipTupleWriteResult.applied("model-1"));
        // Plainly in the past rather than now(): the claim compares this column against the
        // JVM's clock, and this row is written by the database's. A container whose clock has
        // drifted a fraction ahead of the host is enough to leave the job not yet due, which
        // shows up as this test's second pass finding no work and asserting on a stale row.
        jdbc.update(
                """
                        UPDATE source_ingestion_jobs
                        SET available_at = now() - interval '1 minute'
                        WHERE source_revision_id = (
                            SELECT latest_revision_id FROM source_objects WHERE id = ?
                        )
                        """,
                source.id());

        processor.processNext();

        var converged = jdbc.queryForMap(
                """
                        SELECT r.status AS revision_status,
                               kav.status AS asset_status,
                               p.status AS publication_status,
                               p.attempt_count,
                               bool_and(c.active) AS all_chunks_active
                        FROM source_revisions r
                        JOIN knowledge_asset_publication_outbox p ON p.source_revision_id = r.id
                        JOIN knowledge_asset_versions kav ON kav.id = p.knowledge_asset_version_id
                        JOIN knowledge_chunks c
                          ON c.knowledge_asset_version_id = kav.id
                        WHERE r.source_object_id = ?
                        GROUP BY r.status, kav.status, p.status, p.attempt_count
                        """,
                source.id());
        assertEquals("READY", converged.get("revision_status"));
        assertEquals("ACTIVE", converged.get("asset_status"));
        assertEquals("APPLIED", converged.get("publication_status"));
        assertEquals(2, converged.get("attempt_count"));
        assertEquals(true, converged.get("all_chunks_active"));
    }

    @Test
    @SuppressWarnings("SqlResolve")
    void rejectsAnExistingProfileKeyBoundToDifferentSettings() {
        var requested = new EmbeddingProfileSpec(
                "fixture-provider",
                "fixture-model",
                3,
                EmbeddingDistanceMetric.COSINE);
        jdbc.update(
                """
                INSERT INTO embedding_profiles (
                    id, organization_id, profile_key, provider, model,
                    dimensions, distance_metric, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                ORGANIZATION_ID,
                requested.profileKey(),
                "different-provider",
                requested.model(),
                requested.dimensions(),
                requested.distanceMetric().name(),
                OffsetDateTime.now());

        assertThrows(
                IllegalStateException.class,
                () -> embeddingProfiles.resolve(ORGANIZATION_ID, requested));
    }

    private static float[] embedding() {
        float[] values = new float[1536];
        Arrays.fill(values, 0.125F);
        return values;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = SourceUploadService.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.orgmemory\\.core\\.knowledge\\.Source(UploadService|UploadRegistrationService|QueryService)"))
    static class UploadTestConfiguration {

        @Bean
        @Primary
        AiRouteResolver testAiRouteResolver() {
            return workload -> {
                if (workload != AiWorkload.DOCUMENT_EMBEDDING) {
                    throw new IllegalArgumentException("Unsupported test workload: " + workload);
                }
                return new AiRoute("openai", "text-embedding-3-large");
            };
        }
    }
}
