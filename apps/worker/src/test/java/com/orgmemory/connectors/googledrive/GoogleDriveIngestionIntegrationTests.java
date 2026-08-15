package com.orgmemory.connectors.googledrive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import com.orgmemory.core.knowledge.connector.ConnectorCaptureStatus;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.connector.ConnectorIngestionService;
import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;
import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearchConfiguration;
import com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalProperties;
import com.orgmemory.core.knowledge.retrieval.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.secret.SecretValue;
import com.orgmemory.worker.OrgMemoryWorkerApplication;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Recorded Drive responses through the adapter, PostgreSQL ledger, ACL, and retrieval gate. */
@SpringBootTest(
        classes = OrgMemoryWorkerApplication.class,
        properties = {
            "spring.flyway.enabled=true",
            "orgmemory.ingestion.processing.scheduling-enabled=false",
            "orgmemory.ingestion.processing.embedding-provider=fixture",
            "orgmemory.ingestion.processing.embedding-model=fixture-embed",
            "orgmemory.ingestion.processing.embedding-dimensions=3",
            "orgmemory.authorization.convergence.scheduling-enabled=false",
            "orgmemory.graph-rag.indexing.scheduling-enabled=false",
            "orgmemory.graph-rag.postgres.topology-backend=relational",
            "orgmemory.connector.scheduling-enabled=false"
        })
@Import(CanonicalHybridKnowledgeSearchConfiguration.class)
@EnableConfigurationProperties(KnowledgeRetrievalProperties.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GoogleDriveIngestionIntegrationTests {

    private static final UUID ORG = UUID.fromString("fb000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("fb000000-0000-4000-8000-000000000002");
    private static final UUID SPACE = UUID.fromString("fb000000-0000-4000-8000-000000000003");
    private static final UUID CONNECTOR_USER = UUID.fromString("fb000000-0000-4000-8000-000000000004");
    private static final UUID AN_USER = UUID.fromString("fb000000-0000-4000-8000-00000000000a");
    private static final UUID BOB_USER = UUID.fromString("fb000000-0000-4000-8000-00000000000b");
    private static final String CONNECTION = "example.com";
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
    CanonicalHybridKnowledgeSearch retrieval;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void budgetHitPreservesPermissionsAndRevokesWithoutRematerializing() throws Exception {
        seedDirectory();
        stubPorts();
        ConnectorConnectionDirectory connections = mock(ConnectorConnectionDirectory.class);
        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.of(SecretValue.of(serviceAccountKeyJson())));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-15T09:00:00Z"));
        GoogleDriveConnectorBatchSource source = new GoogleDriveConnectorBatchSource(
                connections, builder, new tools.jackson.databind.ObjectMapper(), clock);
        when(connections.enabledCrawls("google_drive"))
                .thenReturn(List.of(configuration("{\"maxFiles\":500}", null)));
        expectToken(server);
        expectList(server, INITIAL_FILES);
        expectExport(server, "1-phoenix", "Project Phoenix budget code is OM-7429-Z.");
        expectExport(server, "2-retained", "Retained operating guide remains active.");

        ConnectorCrawlBatch initialBatch = source.pendingBatches().batches().getFirst();
        ConnectorIngestionResult initial = connector.ingest(initialBatch);

        assertTrue(initial.failures().isEmpty(), () -> "unexpected initial failures: " + initial.failures());
        assertEquals(List.of("1-phoenix", "2-retained"), initial.materialized());
        assertTrue(sees(AN_USER, "OM-7429-Z"));
        assertFalse(sees(BOB_USER, "OM-7429-Z"));
        UUID initialRevision = currentRevision("1-phoenix");
        server.verify();

        server.reset();
        clock.advance(Duration.ofMinutes(5));
        when(connections.enabledCrawls("google_drive"))
                .thenReturn(List.of(configuration(
                        "{\"maxFiles\":500,\"maxBatchBytes\":26214400}", clock.instant())));
        expectList(server, BUDGET_HIT_FILES);
        expectExport(server, "1-phoenix", "Project Phoenix budget code is OM-7429-Z.");
        expectExport(server, "3-crossing", "x".repeat(GoogleDriveApiClient.MAX_BODY_BYTES));

        ConnectorCrawlBatch budgetHit = source.pendingBatches().batches().getFirst();
        assertEquals(
                ConnectorCaptureStatus.INCOMPLETE,
                budgetHit.componentState(ConnectorSyncComponent.CONTENT).captureStatus());
        assertEquals(
                "GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED",
                budgetHit.componentState(ConnectorSyncComponent.CONTENT).incompleteReason());
        assertEquals(
                ConnectorCaptureStatus.COMPLETE,
                budgetHit.componentState(ConnectorSyncComponent.PERMISSION).captureStatus());
        assertEquals(3, budgetHit.permissions().size());
        assertFalse(budgetHit.crawlComplete());

        ConnectorIngestionResult recrawl = connector.ingest(budgetHit);

        assertTrue(recrawl.failures().isEmpty(), () -> "unexpected recrawl failures: " + recrawl.failures());
        assertTrue(recrawl.retired().isEmpty(), "an incomplete content pass has no retirement authority");
        assertEquals(initialRevision, currentRevision("1-phoenix"));
        assertTrue(recrawl.rematerialized().isEmpty(), "permission revocation must not rematerialize text");
        assertFalse(sees(AN_USER, "OM-7429-Z"), "the removed direct reader is revoked");
        assertTrue(sees(BOB_USER, "OM-7429-Z"), "the replacement direct reader is allowed");
        assertTrue(sees(AN_USER, "Retained operating guide"), "omitted evidence was not retired");
        assertEquals("ACTIVE", sourceStatus("2-retained"));
        assertEquals(0L, sourceCount("3-crossing"));
        assertEquals(0L, sourceCount("4-permission-tail"));
        server.verify();
    }

    private ConnectorCrawlConfiguration configuration(String sourceConfig, Instant requestedAt) {
        return new ConnectorCrawlConfiguration(
                ORG,
                "google_drive",
                CONNECTION,
                SPACE,
                CONNECTOR_USER,
                sourceConfig,
                Duration.ofHours(1),
                requestedAt);
    }

    private boolean sees(UUID userId, String query) {
        CurrentActor actor = new CurrentActor(userId, ORG, DEPT, "User " + userId, emailOf(userId));
        return !retrieval.search(actor, query, 10, "req-" + userId + "-" + query.hashCode())
                .evidence()
                .isEmpty();
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
                            "SELECT id FROM knowledge_assets WHERE organization_id = ? "
                                    + "AND archived_at IS NULL AND current_version_id IS NOT NULL",
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

    private void seedDirectory() {
        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) "
                        + "VALUES (?, 'Drive Fixture Org', now(), now(), 0)",
                ORG);
        jdbc.update(
                "INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'Drive Fixture Dept', now(), now(), 0)",
                DEPT,
                ORG);
        insertUser(CONNECTOR_USER, "connector@example.com");
        insertUser(AN_USER, "an@example.com");
        insertUser(BOB_USER, "bob@example.com");
        jdbc.update(
                "INSERT INTO knowledge_spaces (id, organization_id, department_id, audience_mode, "
                        + "audience_version, space_key, name, active, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, 'DEPARTMENT', 1, 'drive-fixture', 'Drive Fixture', true, now(), now(), 0)",
                SPACE,
                ORG,
                DEPT);
    }

    private void insertUser(UUID id, String email) {
        jdbc.update(
                "INSERT INTO app_users (id, organization_id, department_id, name, email, clearance, "
                        + "active, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, ?, 'STANDARD', true, now(), now(), 0)",
                id,
                ORG,
                DEPT,
                email,
                email);
    }

    private UUID currentRevision(String externalObjectId) {
        return jdbc.queryForObject(
                "SELECT current_revision_id FROM source_objects "
                        + "WHERE organization_id = ? AND external_object_id = ?",
                UUID.class,
                ORG,
                externalObjectId);
    }

    private String sourceStatus(String externalObjectId) {
        return jdbc.queryForObject(
                "SELECT status FROM source_objects WHERE organization_id = ? AND external_object_id = ?",
                String.class,
                ORG,
                externalObjectId);
    }

    private long sourceCount(String externalObjectId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM source_objects WHERE organization_id = ? AND external_object_id = ?",
                Long.class,
                ORG,
                externalObjectId);
    }

    private static String emailOf(UUID userId) {
        return userId.equals(AN_USER) ? "an@example.com" : "bob@example.com";
    }

    private static void expectToken(MockRestServiceServer server) {
        server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString("oauth2.googleapis.com/token")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"ya29.not-a-real-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }

    private static void expectList(MockRestServiceServer server, String body) {
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("google-apps.document")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static void expectExport(MockRestServiceServer server, String fileId, String body) {
        server.expect(
                        ExpectedCount.once(),
                        requestTo(Matchers.containsString("/files/" + fileId + "/export")))
                .andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
    }

    private static String serviceAccountKeyJson() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        return """
                {
                  "type": "service_account",
                  "project_id": "orgmemory-test",
                  "client_email": "crawler@orgmemory-test.iam.gserviceaccount.com",
                  "token_uri": "https://oauth2.googleapis.com/token",
                  "private_key": "%s"
                }
                """.formatted(pem);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final String INITIAL_FILES = """
            {"files":[
              {"id":"1-phoenix","name":"Project Phoenix budget",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"permissionId":"owner-p","emailAddress":"owner@example.com"}],
               "permissions":[{"id":"p-an","type":"user","emailAddress":"an@example.com","role":"reader"}]},
              {"id":"2-retained","name":"Retained guide",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"permissionId":"owner-p","emailAddress":"owner@example.com"}],
               "permissions":[{"id":"p-an","type":"user","emailAddress":"an@example.com","role":"reader"}]}
            ]}
            """;

    private static final String BUDGET_HIT_FILES = """
            {"files":[
              {"id":"1-phoenix","name":"Project Phoenix budget",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"permissionId":"owner-p","emailAddress":"owner@example.com"}],
               "permissions":[{"id":"p-bob","type":"user","emailAddress":"bob@example.com","role":"reader"}]},
              {"id":"3-crossing","name":"Crossing body",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"permissionId":"owner-p","emailAddress":"owner@example.com"}],
               "permissions":[{"id":"p-bob","type":"user","emailAddress":"bob@example.com","role":"reader"}]},
              {"id":"4-permission-tail","name":"Permission tail",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"permissionId":"owner-p","emailAddress":"owner@example.com"}],
               "permissions":[{"id":"p-bob","type":"user","emailAddress":"bob@example.com","role":"reader"}]}
            ]}
            """;

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
