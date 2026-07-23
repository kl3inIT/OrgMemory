package com.orgmemory.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.orgmemory.core.knowledge.ConnectorTombstone;
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
 * Proves that an edit at the source reaches retrieval. The staging slice rotated the ACL and
 * reported the changed content revision as deferred, which left the index answering from text
 * the source no longer had. A changed revision now materializes a new current source revision
 * on the same object, and because retrieval only serves chunks belonging to the current
 * revision, the superseded text stops being answerable in the same commit.
 *
 * <p>Also pins the deliberate refusal: an object retired by a tombstone does not quietly come
 * back to life because a later crawl carried content for it. The two tests share a database and
 * work on separate objects with separate vocabulary so neither can answer for the other.
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
class ConnectorContentEditIntegrationTests {

    private static final UUID ORG = UUID.fromString("e2000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("e2000000-0000-4000-8000-000000000002");
    private static final UUID SPACE = UUID.fromString("e2000000-0000-4000-8000-000000000003");
    private static final UUID CONNECTOR_USER = UUID.fromString("e2000000-0000-4000-8000-000000000004");
    private static final UUID MAI_USER = UUID.fromString("e2000000-0000-4000-8000-00000000000e");
    private static final String MAI_EMAIL = "mai@editfix.example";
    private static final String CONNECTION_KEY = "T-edit-workspace";

    private static final String RELEASE_OBJECT = "C-release-msg";
    private static final String RELEASE_CHANNEL = "C-release";
    private static final String RELEASE_ORIGINAL =
            "The release window opens on Tuesday and the rollback owner is the platform team.";
    private static final String RELEASE_EDITED =
            "The release window moved to Thursday and the rollback owner is the payments team.";

    private static final String INCIDENT_OBJECT = "C-incident-msg";
    private static final String INCIDENT_CHANNEL = "C-incident";
    private static final String INCIDENT_ORIGINAL =
            "The database failover postmortem is filed under the reliability retrospective index.";
    private static final String INCIDENT_EDITED =
            "The database failover postmortem was withdrawn pending a legal confidentiality review.";

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
    void anEditedMessageStopsBeingAnsweredFromTheTextTheCrawlFirstSaw() throws Exception {
        seedDirectory();
        stubPorts();

        ConnectorIngestionResult initial = connector.ingest(
                crawl("cursor-release-1", RELEASE_OBJECT, RELEASE_CHANNEL, RELEASE_ORIGINAL, "rev-1"));
        assertEquals(List.of(RELEASE_OBJECT), initial.materialized());
        assertTrue(answers("Tuesday"), "the first crawl's text is answerable");

        UUID revisionAfterInitial = currentRevisionId(RELEASE_OBJECT);
        long generationAfterInitial = aclGeneration(RELEASE_OBJECT);

        ConnectorIngestionResult edit = connector.ingest(
                crawl("cursor-release-2", RELEASE_OBJECT, RELEASE_CHANNEL, RELEASE_EDITED, "rev-2"));
        assertEquals(
                List.of(RELEASE_OBJECT), edit.rematerialized(), () -> "unexpected failures: " + edit.failures());
        assertTrue(edit.rotated().isEmpty(), "a content edit is not a bare ACL rotation");
        assertTrue(edit.materialized().isEmpty(), "the object already existed");

        assertNotEquals(
                revisionAfterInitial, currentRevisionId(RELEASE_OBJECT), "the edit becomes the current revision");
        assertEquals(
                generationAfterInitial + 1,
                aclGeneration(RELEASE_OBJECT),
                "registering the new revision advances the sealed head with it");
        assertEquals(2L, revisionCount(RELEASE_OBJECT), "the superseded revision is retained as evidence");
        assertTrue(answers("Thursday"), "the edited text is answerable");
        assertFalse(answers("Tuesday"), "the superseded text is no longer served");
    }

    @Test
    void aRetiredObjectIsNotRevivedByALaterContentRevision() throws Exception {
        seedDirectory();
        stubPorts();

        connector.ingest(crawl("cursor-incident-1", INCIDENT_OBJECT, INCIDENT_CHANNEL, INCIDENT_ORIGINAL, "rev-1"));
        assertTrue(answers("retrospective"), "the crawled text is answerable before the tombstone");

        ConnectorIngestionResult retire = connector.ingest(tombstone("cursor-incident-2", INCIDENT_OBJECT));
        assertEquals(List.of(INCIDENT_OBJECT), retire.retired());

        ConnectorIngestionResult afterRetire = connector.ingest(
                crawl("cursor-incident-3", INCIDENT_OBJECT, INCIDENT_CHANNEL, INCIDENT_EDITED, "rev-2"));

        assertTrue(afterRetire.rematerialized().isEmpty(), "a retired object takes no new revision");
        assertEquals(1, afterRetire.failures().size(), "the refusal is an isolated per-object failure");
        assertTrue(
                afterRetire.failures().getFirst().reason().contains("retired object"),
                () -> "unexpected reason: " + afterRetire.failures().getFirst().reason());
        assertFalse(answers("confidentiality"), "the retired object stays out of retrieval");
    }

    private ConnectorCrawlBatch crawl(
            String cursor, String objectId, String channelKey, String body, String contentRevision) {
        ConnectorIdentityItem member = new ConnectorIdentityItem(
                SourcePrincipalKind.SOURCE_USER, "U-mai", MAI_EMAIL, "Mai", true, null, null, List.of());
        ConnectorIdentityItem channel = new ConnectorIdentityItem(
                SourcePrincipalKind.SOURCE_GROUP, channelKey, null, "#" + channelKey, false, null, null,
                List.of("U-mai"));
        return new ConnectorCrawlBatch(
                ORG,
                "slack",
                CONNECTION_KEY,
                SPACE,
                CONNECTOR_USER,
                cursor,
                ConnectorContractVersions.supported(),
                List.of(member, channel),
                List.of(new ConnectorContentItem(objectId, "Channel digest", body, contentRevision)),
                List.of(new ConnectorPermissionItem(
                        objectId,
                        List.of(new ConnectorAclGrant(
                                SourcePrincipalKind.SOURCE_GROUP, channelKey, AccessGate.ALLOW)))),
                List.of());
    }

    private ConnectorCrawlBatch tombstone(String cursor, String objectId) {
        return new ConnectorCrawlBatch(
                ORG,
                "slack",
                CONNECTION_KEY,
                SPACE,
                CONNECTOR_USER,
                cursor,
                ConnectorContractVersions.supported(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ConnectorTombstone(objectId)));
    }

    private boolean answers(String term) {
        CurrentActor actor = new CurrentActor(MAI_USER, ORG, DEPT, "Mai", MAI_EMAIL);
        return !retrieval.search(actor, term, 10, "req-" + term).evidence().isEmpty();
    }

    @SuppressWarnings("SqlResolve")
    private UUID currentRevisionId(String objectId) {
        return jdbc.queryForObject(
                "SELECT current_revision_id FROM source_objects "
                        + "WHERE organization_id = ? AND external_object_id = ?",
                UUID.class,
                ORG,
                objectId);
    }

    @SuppressWarnings("SqlResolve")
    private long revisionCount(String objectId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM source_revisions sr "
                        + "JOIN source_objects so ON so.id = sr.source_object_id "
                        + "WHERE so.organization_id = ? AND so.external_object_id = ?",
                Long.class,
                ORG,
                objectId);
    }

    @SuppressWarnings("SqlResolve")
    private long aclGeneration(String objectId) {
        return jdbc.queryForObject(
                "SELECT acl_generation FROM source_acl_heads "
                        + "WHERE organization_id = ? AND external_object_id = ?",
                Long.class,
                ORG,
                objectId);
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

    /** Both tests share one database, so the directory is seeded once for whichever runs first. */
    @SuppressWarnings("SqlResolve")
    private void seedDirectory() {
        Long existing = jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Long.class, ORG);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Edit Fixture Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Edit Fixture Dept', now(), now(), 0)", DEPT, ORG);
        insertUser(CONNECTOR_USER, "connector@editfix.example");
        insertUser(MAI_USER, MAI_EMAIL);
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'edit-space', 'Edit Space', true, now(), now(), 0)
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
