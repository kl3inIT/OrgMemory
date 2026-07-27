package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresAuthorizedGraphSqlTests {

    @Test
    void graphVisibilityUsesTheLatestSealedCompleteAclAfterFreshnessExpiry() {
        String sql = PostgresAuthorizedGraphSql.VISIBLE_KNOWLEDGE_CHUNKS;

        assertTrue(sql.contains("current_snapshot.capture_status = 'COMPLETE'"));
        assertTrue(sql.contains("source_acl_snapshot_seals"));
        assertFalse(
                sql.contains("current_snapshot.valid_until >"),
                "ADR 0015 treats expiry as health evidence, not an authorization gate");
    }
}
