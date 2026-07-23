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
import java.util.ArrayList;
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
 * Proves deletion detection and, more importantly, its refusals. A channel deleted at the
 * source leaves no tombstone behind it — the only evidence is that an exhaustive crawl stopped
 * mentioning it — so absence has to be actionable. It also has to be dangerous: the same
 * absence is what a half-finished crawl, a permissions-only pass, or an adapter that lost its
 * token produces, and acting on those would retire the whole connection.
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
class ConnectorPruningIntegrationTests {

    private static final UUID ORG = UUID.fromString("e4000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("e4000000-0000-4000-8000-000000000002");
    private static final UUID SPACE = UUID.fromString("e4000000-0000-4000-8000-000000000003");
    private static final UUID CONNECTOR_USER = UUID.fromString("e4000000-0000-4000-8000-000000000004");
    private static final UUID LAN_USER = UUID.fromString("e4000000-0000-4000-8000-00000000000f");
    private static final String LAN_EMAIL = "lan@prunefix.example";
    private static final String CONNECTION = "T-prune-workspace";
    private static final String KEPT = "C-kept-msg";
    private static final String DELETED = "C-deleted-msg";
    private static final String KEPT_BODY = "The procurement approval ladder is documented for every vendor tier.";
    private static final String DELETED_BODY = "The abandoned pilot channel discussed a warehouse robotics trial.";
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
    void aCompleteCrawlRetiresWhatTheSourceNoLongerHas() throws Exception {
        seedDirectory();
        stubPorts();

        connector.ingest(crawl("cursor-both", List.of(KEPT, DELETED), false));
        assertTrue(answers("procurement"), "both channels are indexed");
        assertTrue(answers("robotics"), "both channels are indexed");

        // The same crawl a day later: the pilot channel is gone and no tombstone announced it.
        ConnectorIngestionResult pruned = connector.ingest(crawl("cursor-kept-only", List.of(KEPT), true));

        assertEquals(List.of(DELETED), pruned.retired(), () -> "unexpected failures: " + pruned.failures());
        assertTrue(pruned.failures().isEmpty());
        assertTrue(answers("procurement"), "the surviving channel is untouched");
        assertFalse(answers("robotics"), "the deleted channel leaves retrieval");
        assertEquals("ARCHIVED", statusOf(DELETED));
        assertEquals("ACTIVE", statusOf(KEPT));
    }

    @Test
    void anIncompleteCrawlRetiresNothingItSimplyDidNotMention() throws Exception {
        seedDirectory();
        stubPorts();

        connector.ingest(crawl("cursor-seed-partial", List.of(KEPT, DELETED), false));

        // A crawl that got through one channel before stopping looks exactly like a deletion.
        ConnectorIngestionResult partial = connector.ingest(crawl("cursor-partial", List.of(KEPT), false));

        assertTrue(partial.retired().isEmpty(), "a crawl that claims nothing may retire nothing");
        assertEquals("ACTIVE", statusOf(DELETED));
        assertTrue(answers("robotics"), "the unmentioned channel is still answerable");
    }

    @Test
    void aCompleteCrawlThatEnumeratedNothingIsRefused() throws Exception {
        seedDirectory();
        stubPorts();

        connector.ingest(crawl("cursor-seed-empty", List.of(KEPT, DELETED), false));

        // What a revoked token or a bot removed from every channel produces.
        ConnectorIngestionResult empty = connector.ingest(crawl("cursor-empty", List.of(), true));

        assertTrue(empty.retired().isEmpty(), "an empty complete crawl retires nothing");
        assertEquals(1, empty.failures().size(), "the refusal is reported rather than silent");
        assertTrue(
                empty.failures().getFirst().reason().contains("refused to prune"),
                () -> "unexpected reason: " + empty.failures().getFirst().reason());
        assertEquals("ACTIVE", statusOf(KEPT));
        assertEquals("ACTIVE", statusOf(DELETED));
    }

    private ConnectorCrawlBatch crawl(String cursor, List<String> objectIds, boolean complete) {
        List<ConnectorIdentityItem> identities = new ArrayList<>();
        identities.add(new ConnectorIdentityItem(
                SourcePrincipalKind.SOURCE_USER, "U-lan", LAN_EMAIL, "Lan", true, null, null, List.of()));
        List<ConnectorContentItem> contents = new ArrayList<>();
        List<ConnectorPermissionItem> permissions = new ArrayList<>();
        for (String objectId : objectIds) {
            String channel = channelOf(objectId);
            identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_GROUP, channel, null, "#" + channel, false, null, null,
                    List.of("U-lan")));
            contents.add(new ConnectorContentItem(objectId, "Channel digest", bodyOf(objectId), "rev-1"));
            permissions.add(new ConnectorPermissionItem(
                    objectId,
                    List.of(new ConnectorAclGrant(SourcePrincipalKind.SOURCE_GROUP, channel, AccessGate.ALLOW))));
        }
        return new ConnectorCrawlBatch(
                ORG,
                "slack",
                CONNECTION,
                SPACE,
                CONNECTOR_USER,
                cursor,
                ConnectorContractVersions.supported(),
                identities,
                contents,
                permissions,
                List.of(),
                complete);
    }

    private static String channelOf(String objectId) {
        return KEPT.equals(objectId) ? "C-kept" : "C-deleted";
    }

    private static String bodyOf(String objectId) {
        return KEPT.equals(objectId) ? KEPT_BODY : DELETED_BODY;
    }

    private boolean answers(String term) {
        CurrentActor actor = new CurrentActor(LAN_USER, ORG, DEPT, "Lan", LAN_EMAIL);
        return !retrieval.search(actor, term, 10, "req-" + term).evidence().isEmpty();
    }

    @SuppressWarnings("SqlResolve")
    private String statusOf(String externalObjectId) {
        return jdbc.queryForObject(
                "SELECT status FROM source_objects WHERE organization_id = ? AND external_object_id = ?",
                String.class,
                ORG,
                externalObjectId);
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

    /**
     * Each test needs both channels back in their indexed state, so the directory is seeded once
     * and any object an earlier test retired is restored before the next one runs.
     */
    @SuppressWarnings("SqlResolve")
    private void seedDirectory() {
        Long existing = jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Long.class, ORG);
        if (existing != null && existing > 0) {
            jdbc.update(
                    "UPDATE source_objects SET status = 'ACTIVE' WHERE organization_id = ? AND status = 'ARCHIVED'",
                    ORG);
            return;
        }
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Prune Fixture Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Prune Fixture Dept', now(), now(), 0)", DEPT, ORG);
        insertUser(CONNECTOR_USER, "connector@prunefix.example");
        insertUser(LAN_USER, LAN_EMAIL);
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'prune-space', 'Prune Space', true, now(), now(), 0)
                """, SPACE, ORG, DEPT);
    }

    @SuppressWarnings("SqlResolve")
    private void insertUser(UUID id, String email) {
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'EMPLOYEE', true, now(), now(), 0)
                """, id, ORG, DEPT, email, email);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
