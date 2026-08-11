package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresAuthorizedGraphSqlTests {

    @Test
    void graphVisibilityUsesTheLatestSealedCompleteAclAfterFreshnessExpiry() {
        String sql = PostgresAuthorizedGraphSql.VISIBLE_KNOWLEDGE_CHUNKS;

        assertTrue(sql.contains("current_snapshot.capture_status = 'COMPLETE'"));
        assertTrue(sql.contains("JOIN source_acl_snapshot_seals current_seal"));
        assertTrue(sql.contains("current_seal.source_acl_snapshot_id ="));
        assertTrue(sql.contains("current_snapshot.id"));
        assertFalse(
                sql.contains("current_snapshot.valid_until >"),
                "ADR 0015 treats expiry as health evidence, not an authorization gate");
    }

    @Test
    void graphVisibilityRestrictsSelectedEvidenceBeforeTraversal() {
        String sql = PostgresAuthorizedGraphSql.VISIBLE_KNOWLEDGE_CHUNKS;

        assertTrue(sql.contains(":exactEvidenceRestricted = FALSE"));
        assertTrue(sql.contains(
                "chunk.source_revision_id IN (:selectedSourceRevisionIds)"));
    }
}
