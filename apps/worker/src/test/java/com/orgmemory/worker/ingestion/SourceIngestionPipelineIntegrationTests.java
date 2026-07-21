package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.CreateUploadSourceCommand;
import com.orgmemory.core.knowledge.SourceUploadService;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "orgmemory.ingestion.processing.scheduling-enabled=false",
        "orgmemory.ingestion.processing.embedding-model=text-embedding-3-large",
        "orgmemory.ingestion.processing.embedding-dimensions=1536"
})
@Import(SourceIngestionPipelineIntegrationTests.UploadTestConfiguration.class)
@Testcontainers
class SourceIngestionPipelineIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, DEPARTMENT_ID, "Linh Nguyen", "linh@example.com");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ObjectStoragePort objects;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    SourceUploadService uploads;

    @Autowired
    SourceIngestionProcessor processor;

    @Autowired
    JdbcTemplate jdbc;

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

        var source = uploads.upload(
                new CreateUploadSourceCommand(
                        ACTOR,
                        "support-resolution.txt",
                        "text/plain",
                        content.length,
                        KnowledgeClassification.CONFIDENTIAL),
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
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_chunks WHERE source_object_id = ?",
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
                        "SELECT status FROM source_ingestion_jobs WHERE source_revision_id = (SELECT current_revision_id FROM source_objects WHERE id = ?)",
                        String.class,
                        source.id()));
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
    }
}
