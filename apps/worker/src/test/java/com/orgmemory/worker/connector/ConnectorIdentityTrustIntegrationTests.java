package com.orgmemory.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

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
import com.orgmemory.core.knowledge.ConnectorAclGrant;
import com.orgmemory.core.knowledge.ConnectorContentItem;
import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import com.orgmemory.core.knowledge.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.KnowledgeRetrievalProperties;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.AccessGate;
import java.io.InputStream;
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
 * Proves that an administrator's per-connection identity-trust decision reaches the crawl. The
 * crawled workspace does not vouch for the emails it reports, so the first crawl leaves its
 * member unmapped and the sealed channel grant resolves to nobody. Nothing about the source
 * then changes — no new content, no membership edit, not even a rotated ACL generation — only
 * the administrator's standing decision on the connection, after which the same identity
 * payload binds and the same sealed grant opens.
 *
 * <p>This is the wiring the unit tests cannot reach: that the reconciler reads
 * {@code source_connections} at all, and that an absent row is treated as untrusted.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "orgmemory.ingestion.processing.scheduling-enabled=false",
        "orgmemory.ingestion.processing.embedding-provider=fixture",
        "orgmemory.ingestion.processing.embedding-model=fixture-embed",
        "orgmemory.ingestion.processing.embedding-dimensions=3",
        "orgmemory.connector.scheduling-enabled=false"
})
@Import(SecureKnowledgeRetrievalService.class)
@EnableConfigurationProperties(KnowledgeRetrievalProperties.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConnectorIdentityTrustIntegrationTests {

    private static final UUID ORG = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("e1000000-0000-4000-8000-000000000002");
    private static final UUID SPACE = UUID.fromString("e1000000-0000-4000-8000-000000000003");
    private static final UUID CONNECTOR_USER = UUID.fromString("e1000000-0000-4000-8000-000000000004");
    private static final UUID ADMIN_USER = UUID.fromString("e1000000-0000-4000-8000-000000000005");
    private static final UUID DUNG_USER = UUID.fromString("e1000000-0000-4000-8000-00000000000d");
    private static final String DUNG_EMAIL = "dung@trustfix.example";
    private static final String CONNECTION_KEY = "T-untrusting-workspace";
    private static final String OBJECT_ID = "C-handbook-msg";
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

    @Test
    @SuppressWarnings("SqlResolve")
    void administratorTrustDecisionOpensAnUnvouchedEmailJoin() throws Exception {
        seedDirectory();
        stubPorts();

        ConnectorIngestionResult initial = connector.ingest(crawl("cursor-untrusted", true));
        assertEquals(List.of(OBJECT_ID), initial.materialized());
        assertTrue(initial.failures().isEmpty(), () -> "unexpected failures: " + initial.failures());
        assertEquals(0L, activeMappings(), "no connection decision exists, so the email may not carry a join");
        assertFalse(sees(DUNG_USER), "an unmapped principal grants nothing even though it is a channel member");

        long generationBeforeDecision = aclGeneration();
        attestConnection();

        // Identities only: no content, no permissions, no rotation. The administrator's decision
        // is the sole difference between this crawl and the last one.
        ConnectorIngestionResult afterDecision = connector.ingest(crawl("cursor-attested", false));
        assertTrue(afterDecision.materialized().isEmpty(), "an identity-only crawl materializes nothing");
        assertTrue(afterDecision.rotated().isEmpty(), "an identity-only crawl rotates nothing");
        assertEquals(generationBeforeDecision, aclGeneration(), "the sealed grant is untouched");
        assertEquals(1L, activeMappings(), "the attested connection lets the observed email bind");
        assertTrue(sees(DUNG_USER), "the same sealed grant now resolves through the new mapping");
        assertEquals(
                "SSO_EMAIL_JOIN",
                jdbc.queryForObject(
                        "SELECT method FROM source_principal_mappings WHERE organization_id = ? AND status = 'ACTIVE'",
                        String.class,
                        ORG));
    }

    /**
     * One crawl of a workspace that reports its member without vouching for the address. The
     * first pass carries the channel and its grant; the second carries identities alone.
     */
    private ConnectorCrawlBatch crawl(String cursor, boolean withContent) {
        ConnectorIdentityItem member = new ConnectorIdentityItem(
                SourcePrincipalKind.SOURCE_USER, "U-dung", DUNG_EMAIL, "Dung", false, null, null, List.of());
        ConnectorIdentityItem channel = new ConnectorIdentityItem(
                SourcePrincipalKind.SOURCE_GROUP, "C-handbook", null, "#handbook", false, null, null,
                List.of("U-dung"));
        return new ConnectorCrawlBatch(
                ORG,
                "slack",
                CONNECTION_KEY,
                SPACE,
                CONNECTOR_USER,
                cursor,
                ConnectorContractVersions.supported(),
                List.of(member, channel),
                withContent
                        ? List.of(new ConnectorContentItem(
                                OBJECT_ID,
                                "Handbook digest",
                                "The quarterly onboarding runbook covers laptop setup, VPN access, "
                                        + "and the first-week checklist.",
                                "rev-1"))
                        : List.of(),
                withContent
                        ? List.of(new ConnectorPermissionItem(
                                OBJECT_ID,
                                List.of(new ConnectorAclGrant(
                                        SourcePrincipalKind.SOURCE_GROUP, "C-handbook", AccessGate.ALLOW))))
                        : List.of(),
                List.of());
    }

    @SuppressWarnings("SqlResolve")
    private void attestConnection() {
        jdbc.update("""
                INSERT INTO source_connections (
                    id, organization_id, source_system, source_connection_key, identity_trust,
                    trust_decided_by_user_id, trust_decided_at, created_at, updated_at, version)
                VALUES (?, ?, 'slack', ?, 'SSO_VERIFIED', ?, now(), now(), now(), 0)
                """, UUID.randomUUID(), ORG, CONNECTION_KEY, ADMIN_USER);
    }

    private boolean sees(UUID userId) {
        CurrentActor actor = new CurrentActor(userId, ORG, DEPT, "User " + userId, DUNG_EMAIL);
        return !retrieval.search(actor, "onboarding runbook", 10, "req-" + userId).evidence().isEmpty();
    }

    @SuppressWarnings("SqlResolve")
    private long activeMappings() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM source_principal_mappings WHERE organization_id = ? AND status = 'ACTIVE'",
                Long.class,
                ORG);
    }

    @SuppressWarnings("SqlResolve")
    private long aclGeneration() {
        return jdbc.queryForObject(
                "SELECT acl_generation FROM source_acl_heads "
                        + "WHERE organization_id = ? AND external_object_id = ?",
                Long.class,
                ORG,
                OBJECT_ID);
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
                            "SELECT id FROM knowledge_assets WHERE organization_id = ? AND status = 'ACTIVE'",
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

    @SuppressWarnings("SqlResolve")
    private void seedDirectory() {
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Trust Fixture Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Trust Fixture Dept', now(), now(), 0)", DEPT, ORG);
        insertUser(CONNECTOR_USER, "connector@trustfix.example", "EMPLOYEE");
        insertUser(ADMIN_USER, "admin@trustfix.example", "ADMIN");
        insertUser(DUNG_USER, DUNG_EMAIL, "EMPLOYEE");
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'trust-space', 'Trust Space', true, now(), now(), 0)
                """, SPACE, ORG, DEPT);
    }

    @SuppressWarnings("SqlResolve")
    private void insertUser(UUID id, String email, String role) {
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, true, now(), now(), 0)
                """, id, ORG, DEPT, email, email, role);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
