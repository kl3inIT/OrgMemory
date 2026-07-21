package com.orgmemory.api.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.CreateUploadSourceCommand;
import com.orgmemory.core.knowledge.SourceIngestionCoordinator;
import com.orgmemory.core.knowledge.SourceQueryService;
import com.orgmemory.core.knowledge.SourceRevisionStatus;
import com.orgmemory.core.knowledge.SourceUploadService;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SourceUploadIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, DEPARTMENT_ID, "Linh Nguyen", "linh@example.com");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ObjectStoragePort objects;

    @Autowired
    SourceUploadService uploads;

    @Autowired
    SourceQueryService sources;

    @Autowired
    SourceIngestionCoordinator coordinator;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("SqlResolve")
    void uploadCreatesCanonicalSourceRevisionBlobAndDurableJob() throws Exception {
        byte[] content = "Approved onboarding workflow".getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        when(objects.put(any(), any())).thenAnswer(invocation -> {
            var request = (com.orgmemory.core.knowledge.storage.ObjectWriteRequest) invocation.getArgument(0);
            return new StoredObject(
                    request.key(), content.length, "text/plain", sha, "etag-1", null);
        });

        var uploaded = uploads.upload(
                new CreateUploadSourceCommand(
                        ACTOR,
                        "onboarding.txt",
                        "text/plain",
                        content.length,
                        KnowledgeClassification.CONFIDENTIAL),
                new ByteArrayInputStream(content));

        assertEquals(SourceRevisionStatus.RECEIVED, uploaded.status());
        assertEquals(1, sources.listOwn(ACTOR).size());
        assertTrue(sources.listOwn(new CurrentActor(
                        OTHER_USER_ID,
                        ORGANIZATION_ID,
                        DEPARTMENT_ID,
                        "Minh Tran",
                        "minh@example.com"))
                .isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM source_objects", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM source_revisions", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM evidence_blobs", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM source_ingestion_jobs", Integer.class));

        var claim = coordinator.claimNext("test-worker", Duration.ofMinutes(1)).orElseThrow();
        assertEquals(uploaded.id(), claim.sourceObjectId());
        assertEquals(sha, claim.contentSha256());
        assertTrue(claim.objectKey().startsWith("organizations/" + ORGANIZATION_ID + "/sources/"));
    }
}
