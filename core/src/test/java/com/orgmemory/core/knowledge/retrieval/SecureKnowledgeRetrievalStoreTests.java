package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SecureKnowledgeRetrievalStoreTests {

    @Test
    @SuppressWarnings("unchecked")
    void exactEvidenceIdentityIsAppliedBeforeLexicalRankingAndLimit() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of());
        SecureKnowledgeRetrievalStore store = new SecureKnowledgeRetrievalStore(jdbc);
        UUID sourceObjectId = UUID.randomUUID();
        UUID sourceRevisionId = UUID.randomUUID();
        var scope = new SecureKnowledgeRetrievalStore.RetrievalScope(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                false,
                List.of(UUID.randomUUID()),
                "model-v1",
                Instant.parse("2026-08-11T00:00:00Z"),
                List.of(sourceObjectId),
                List.of(sourceRevisionId));

        store.lexical(scope, "leave policy", 10);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().indexOf("kc.source_revision_id IN")
                < sql.getValue().indexOf("ORDER BY retrieval_score"));
        assertTrue(sql.getValue().indexOf("kc.source_object_id IN")
                < sql.getValue().indexOf("LIMIT :candidateLimit"));
        assertEquals(List.of(sourceObjectId),
                parameters.getValue().getValue("selectedSourceObjectIds"));
        assertEquals(List.of(sourceRevisionId),
                parameters.getValue().getValue("selectedSourceRevisionIds"));
    }
}
