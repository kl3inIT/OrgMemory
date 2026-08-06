package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class PostgresGraphRagCacheStoreTests {

    @Test
    void prunesExpiredAndOverflowRowsWithinOneNamespaceAndOperation() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        PostgresGraphRagCacheStore store =
                new PostgresGraphRagCacheStore(jdbc, transactions, 2);
        ProjectionNamespace namespace = new ProjectionNamespace(
                UUID.randomUUID(), "default", "knowledge");
        Instant now = Instant.parse("2026-08-06T00:00:00Z");

        store.prune(namespace, "QUERY_EMBEDDING", now, 10_000);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("target.expires_at <= :now"));
        assertTrue(sql.getValue().contains("OFFSET :maximumEntries"));
        assertEquals(namespace.organizationId(), parameters.getValue().getValue("organizationId"));
        assertEquals("QUERY_EMBEDDING", parameters.getValue().getValue("operation"));
        assertEquals(10_000, parameters.getValue().getValue("maximumEntries"));
    }

    @Test
    void batchesOnlyEvidenceAfterUpsertAndDeleteWhileKeepingGlobalOrdinals() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        UUID entryId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(entryId);
        PostgresGraphRagCacheStore store =
                new PostgresGraphRagCacheStore(jdbc, transactions, 2);

        UUID organizationId = UUID.randomUUID();
        ProjectionSnapshot snapshot = new ProjectionSnapshot(
                UUID.randomUUID(),
                new ProjectionNamespace(organizationId, "default", "knowledge"),
                1,
                "manifest",
                Set.of(ProjectionKind.CONTENT),
                Instant.parse("2026-08-01T00:00:00Z"));
        RetrievalResultCache.Key key = new RetrievalResultCache.Key(
                snapshot,
                "a".repeat(64),
                "b".repeat(64),
                "secure-mix",
                "model-route");
        List<EvidenceReference> evidence = List.of(
                evidence(organizationId),
                evidence(organizationId),
                evidence(organizationId));
        RetrievalResultCache.Entry entry = new RetrievalResultCache.Entry(
                "application/json",
                "{}",
                evidence,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:05:00Z"));

        store.put(key, entry);

        var ordered = inOrder(jdbc);
        ordered.verify(jdbc)
                .queryForObject(anyString(), any(SqlParameterSource.class), eq(UUID.class));
        ordered.verify(jdbc).update(anyString(), any(SqlParameterSource.class));
        ordered.verify(jdbc, times(2))
                .batchUpdate(anyString(), any(SqlParameterSource[].class));
        ArgumentCaptor<SqlParameterSource[]> batches =
                ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc, times(2)).batchUpdate(anyString(), batches.capture());
        assertEquals(List.of(2, 1), batches.getAllValues().stream().map(batch -> batch.length).toList());
        assertEquals(
                List.of(0, 1, 2),
                batches.getAllValues().stream()
                        .flatMap(java.util.Arrays::stream)
                        .map(parameters -> (Integer) parameters.getValue("ordinal"))
                        .toList());
    }

    private static EvidenceReference evidence(UUID organizationId) {
        return new EvidenceReference(
                organizationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1);
    }
}
