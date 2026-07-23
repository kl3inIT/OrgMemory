package com.orgmemory.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import com.orgmemory.core.knowledge.KnowledgeRetrievalProperties;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Drives {@link ConnectorIngestionService} with the committed Slack fixtures against a real
 * PostgreSQL and asserts the governed convergence contract end to end: an initial crawl makes
 * a channel searchable only to its mapped members, a permissions-only re-crawl converges a
 * membership change (An leaves, Chi joins) without re-materializing content, and a tombstone
 * retires the object. Retrieval runs through the real SecureKnowledgeRetrievalService with
 * OpenFGA policy mocked to allow, so the sealed source ACL is the deciding gate.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "orgmemory.ingestion.processing.scheduling-enabled=false",
        "orgmemory.ingestion.processing.embedding-provider=fixture",
        "orgmemory.ingestion.processing.embedding-model=fixture-embed",
        "orgmemory.ingestion.processing.embedding-dimensions=3",
        "orgmemory.authorization.convergence.scheduling-enabled=false",
        "orgmemory.connector.scheduling-enabled=false"
})
@Import(SecureKnowledgeRetrievalService.class)
@EnableConfigurationProperties(KnowledgeRetrievalProperties.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConnectorStagingIngestionIntegrationTests {

    private static final UUID ORG = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("e0000000-0000-4000-8000-000000000002");
    private static final UUID SPACE = UUID.fromString("e0000000-0000-4000-8000-000000000003");
    private static final UUID CONNECTOR_USER = UUID.fromString("e0000000-0000-4000-8000-000000000004");
    private static final UUID AN_USER = UUID.fromString("e0000000-0000-4000-8000-00000000000a");
    private static final UUID BOB_USER = UUID.fromString("e0000000-0000-4000-8000-00000000000b");
    private static final UUID CHI_USER = UUID.fromString("e0000000-0000-4000-8000-00000000000c");
    private static final String MODEL_ID = "model-1";

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
    RelationshipAuthorizationPort entryAuthorization;

    @MockitoBean
    RelationshipAuthorizationSetPort setAuthorization;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    @Autowired
    ConnectorIngestionService connector;

    @Autowired
    SecureKnowledgeRetrievalService retrieval;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("SqlResolve")
    void slackChannelBecomesGovernedAndConvergesOnMembership() throws Exception {
        seedDirectory();
        stubPorts();

        ConnectorIngestionResult initial = connector.ingest(load("slack-01-initial-crawl.json"));
        // Asserted before the content: an object that failed reports why, and that reason is
        // more use than an empty list compared against an expected one.
        assertTrue(initial.failures().isEmpty(), () -> "unexpected failures: " + initial.failures());
        assertEquals(List.of("C-general-msg"), initial.materialized());
        assertTrue(sees(AN_USER), "An is a mapped channel member and must see the message");
        assertFalse(sees(BOB_USER), "Bob is mapped but not a channel member");
        assertFalse(sees(CHI_USER), "Chi has not been observed or mapped yet");

        UUID revisionAfterInitial = currentRevisionId();
        long chunksAfterInitial = chunkCount();
        assertEquals(1L, aclGeneration());

        ConnectorIngestionResult recrawl = connector.ingest(load("slack-02-recrawl-membership.json"));
        assertEquals(List.of("C-general-msg"), recrawl.rotated());
        assertTrue(recrawl.materialized().isEmpty(), "a membership re-crawl must not re-materialize content");
        assertTrue(recrawl.rematerialized().isEmpty(), "a permissions-only re-crawl carries no content revision");
        assertTrue(sees(CHI_USER), "Chi joined the channel and must now see the message");
        assertFalse(sees(AN_USER), "An left the channel and must be revoked");
        assertEquals(revisionAfterInitial, currentRevisionId(), "content revision must be unchanged");
        assertEquals(chunksAfterInitial, chunkCount(), "chunks must not be re-materialized");
        assertEquals(2L, aclGeneration(), "the head must advance to the new sealed generation");

        ConnectorIngestionResult tombstone = connector.ingest(load("slack-03-tombstone.json"));
        assertEquals(List.of("C-general-msg"), tombstone.retired());
        assertFalse(sees(CHI_USER), "a tombstoned object drops out of retrieval");
        assertEquals("ARCHIVED", sourceObjectStatus());
    }

    private boolean sees(UUID userId) {
        CurrentActor actor = new CurrentActor(userId, ORG, DEPT, "User " + userId, emailOf(userId));
        return !retrieval.search(actor, "onboarding runbook", 10, "req-" + userId).evidence().isEmpty();
    }

    private void stubPorts() throws Exception {
        when(objects.put(any(), any())).thenAnswer(invocation -> {
            ObjectWriteRequest request = invocation.getArgument(0);
            byte[] bytes = ((InputStream) invocation.getArgument(1)).readAllBytes();
            return new StoredObject(request.key(), bytes.length, request.mediaType(), sha256(bytes), "etag", null);
        });
        when(embeddingModel.embed(anyList(), isNull(), any(TokenCountBatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    List<Document> documents = invocation.getArgument(0);
                    return documents.stream().map(ignored -> new float[] {0.1F, 0.2F, 0.3F}).toList();
                });
        when(relationshipTuples.write(any(RelationshipTupleWriteRequest.class)))
                .thenReturn(RelationshipTupleWriteResult.applied(MODEL_ID));
        when(entryAuthorization.check(any())).thenReturn(AuthorizationDecision.allow(MODEL_ID));
        when(setAuthorization.listAuthorizedResources(any())).thenAnswer(invocation -> {
            List<ResourceRef> resources = jdbc.queryForList(
                            """
                            SELECT id
                            FROM knowledge_assets
                            WHERE organization_id = ?
                              AND archived_at IS NULL
                              AND current_version_id IS NOT NULL
                            """,
                            UUID.class,
                            ORG)
                    .stream()
                    .map(id -> ResourceRef.of(ORG, "knowledge_asset", id))
                    .toList();
            return AuthorizedResourceSetResult.resolved(resources, MODEL_ID);
        });
        when(setAuthorization.batchCheck(any())).thenAnswer(invocation -> {
            BatchAuthorizationQuery query = invocation.getArgument(0);
            Map<ResourceRef, AuthorizationDecision> decisions = new LinkedHashMap<>();
            for (ResourceRef resource : query.resources()) {
                decisions.put(resource, AuthorizationDecision.allow(MODEL_ID));
            }
            return BatchAuthorizationResult.resolved(decisions, MODEL_ID);
        });
        when(queryEmbeddings.embed(any(), any())).thenReturn(Optional.empty());
    }

    private ConnectorCrawlBatch load(String fixture) throws Exception {
        return objectMapper.readValue(
                Files.readAllBytes(ConnectorFixtures.directory().resolve(fixture)), ConnectorCrawlBatch.class);
    }

    private void seedDirectory() {
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Fixture Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Fixture Dept', now(), now(), 0)", DEPT, ORG);
        insertUser(CONNECTOR_USER, "connector@slackfix.example");
        insertUser(AN_USER, "an@slackfix.example");
        insertUser(BOB_USER, "bob@slackfix.example");
        insertUser(CHI_USER, "chi@slackfix.example");
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'fixture-space', 'Fixture Space', true, now(), now(), 0)
                """, SPACE, ORG, DEPT);
    }

    private void insertUser(UUID id, String email) {
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'EMPLOYEE', true, now(), now(), 0)
                """, id, ORG, DEPT, email, email);
    }

    private static String emailOf(UUID userId) {
        if (userId.equals(AN_USER)) {
            return "an@slackfix.example";
        }
        if (userId.equals(BOB_USER)) {
            return "bob@slackfix.example";
        }
        return "chi@slackfix.example";
    }

    private UUID currentRevisionId() {
        return jdbc.queryForObject(
                "SELECT current_revision_id FROM source_objects "
                        + "WHERE organization_id = ? AND external_object_id = 'C-general-msg'",
                UUID.class,
                ORG);
    }

    private long chunkCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_chunks kc "
                        + "JOIN source_objects so ON so.id = kc.source_object_id "
                        + "WHERE so.organization_id = ? AND so.external_object_id = 'C-general-msg'",
                Long.class,
                ORG);
    }

    private long aclGeneration() {
        return jdbc.queryForObject(
                "SELECT acl_generation FROM source_acl_heads "
                        + "WHERE organization_id = ? AND external_object_id = 'C-general-msg'",
                Long.class,
                ORG);
    }

    private String sourceObjectStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM source_objects "
                        + "WHERE organization_id = ? AND external_object_id = 'C-general-msg'",
                String.class,
                ORG);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
