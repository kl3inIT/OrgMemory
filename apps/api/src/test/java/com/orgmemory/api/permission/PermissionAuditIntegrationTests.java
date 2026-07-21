package com.orgmemory.api.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class PermissionAuditIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    PermissionAuditService audit;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void appendsAuditEventAndStoresOnlyQueryFingerprint() {
        String rawQuery = "KPI van han gom nhung chi so nao?";

        UUID eventId = audit.record(command("DOC030", rawQuery));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM permission_audit_events WHERE id = ?", eventId);
        assertEquals(ORGANIZATION_ID, row.get("organization_id"));
        assertEquals(ACTOR_ID, row.get("actor_user_id"));
        assertEquals("SEARCH", row.get("operation"));
        assertEquals("KNOWLEDGE_DOCUMENT", row.get("resource_type"));
        assertEquals("DOC030", row.get("resource_id"));
        assertEquals("ALLOW", row.get("decision"));
        assertEquals("INTERNAL_ACCESS", row.get("reason_code"));
        assertEquals(KnowledgePermissionPolicy.POLICY_VERSION, row.get("policy_version"));
        assertNull(row.get("metadata_json"));
        assertNotNull(row.get("occurred_at"));
        String fingerprint = (String) row.get("query_fingerprint");
        assertNotEquals(rawQuery, fingerprint);
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    @Test
    void requiresNewAuditCommitSurvivesOuterRollback() {
        UUID[] eventId = new UUID[1];
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            eventId[0] = audit.record(command("DOC031", "restricted plan"));
            status.setRollbackOnly();
        });

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM permission_audit_events WHERE id = ?",
                        Long.class,
                        eventId[0]));
    }

    @Test
    void databaseRejectsUpdateDeleteAndTruncate() {
        UUID eventId = audit.record(command("DOC032", null));
        long countBefore = jdbc.queryForObject("SELECT count(*) FROM permission_audit_events", Long.class);

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE permission_audit_events SET reason_code = 'CHANGED' WHERE id = ?",
                        eventId));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("DELETE FROM permission_audit_events WHERE id = ?", eventId));
        assertThrows(DataAccessException.class, () -> jdbc.execute("TRUNCATE permission_audit_events"));

        assertEquals(countBefore, jdbc.queryForObject("SELECT count(*) FROM permission_audit_events", Long.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM permission_audit_events WHERE id = ?",
                        Long.class,
                        eventId));
    }

    @Test
    void databaseRejectsFreeFormAuditMetadata() {
        UUID eventId = UUID.randomUUID();

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO permission_audit_events (
                            id, organization_id, actor_user_id, operation, resource_type, resource_id,
                            decision, reason_code, policy_version, metadata_json, occurred_at
                        ) VALUES (?, ?, ?, 'SEARCH', 'KNOWLEDGE_DOCUMENT', 'DOC099',
                            'DENY', 'TEST', 'knowledge-v1', '{"rawQuery":"secret"}', now())
                        """,
                        eventId,
                        ORGANIZATION_ID,
                        ACTOR_ID));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM permission_audit_events WHERE id = ?",
                        Long.class,
                        eventId));
    }

    private static PermissionAuditCommand command(String resourceId, String queryText) {
        return new PermissionAuditCommand(
                ORGANIZATION_ID,
                ACTOR_ID,
                "SEARCH",
                "KNOWLEDGE_DOCUMENT",
                resourceId,
                PermissionAuditDecision.ALLOW,
                "INTERNAL_ACCESS",
                KnowledgePermissionPolicy.POLICY_VERSION,
                "request-123",
                queryText);
    }
}
