package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.ConnectorReconciler.ObjectOutcome;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.OrganizationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ConnectorIngestionServiceTests {

    private static final UUID ORG = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR = UUID.fromString("c0000000-0000-4000-8000-000000000003");

    private ConnectorReconciler reconciler;
    private OrganizationRepository organizations;
    private AppUserRepository users;
    private KnowledgeSpaceService knowledgeSpaces;
    private ConnectorIngestionService service;

    @BeforeEach
    void setUp() {
        reconciler = mock(ConnectorReconciler.class);
        ConnectorSourceRegistry sources = new ConnectorSourceRegistry(List.of(new ConnectorSourceProfile(
                "slack", "Slack", KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES, "message", "text/plain")));
        organizations = mock(OrganizationRepository.class);
        users = mock(AppUserRepository.class);
        knowledgeSpaces = mock(KnowledgeSpaceService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new ConnectorIngestionService(
                reconciler, sources, organizations, users, knowledgeSpaces, transactionManager);
    }

    @Test
    void unknownPayloadVersionFailsClosedBeforeAnyWork() {
        ConnectorCrawlBatch batch = batch(
                new ConnectorContractVersions("content/v2", "identity/v1", "permissions/v1"),
                "slack",
                List.of(content("C1")),
                List.of());

        assertThrows(UnsupportedConnectorPayloadException.class, () -> service.ingest(batch));
        verify(reconciler, never()).resolveIdentities(any(), any());
        verify(reconciler, never()).reconcile(any(), any(), any(), any());
    }

    @Test
    void unsupportedSourceSystemIsRejected() {
        ConnectorCrawlBatch batch = batch(
                ConnectorContractVersions.supported(), "teams", List.of(content("C1")), List.of());

        assertThrows(UnsupportedConnectorSourceException.class, () -> service.ingest(batch));
        verify(reconciler, never()).resolveIdentities(any(), any());
    }

    @Test
    void unknownOrganizationIsRejected() {
        when(organizations.existsById(ORG)).thenReturn(false);
        ConnectorCrawlBatch batch = batch(
                ConnectorContractVersions.supported(), "slack", List.of(content("C1")), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.ingest(batch));
    }

    @Test
    void inactiveActorIsRejected() {
        when(organizations.existsById(ORG)).thenReturn(true);
        AppUser actor = mock(AppUser.class);
        when(actor.isActive()).thenReturn(false);
        when(actor.getOrganizationId()).thenReturn(ORG);
        when(users.findById(ACTOR)).thenReturn(Optional.of(actor));
        ConnectorCrawlBatch batch = batch(
                ConnectorContractVersions.supported(), "slack", List.of(content("C1")), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.ingest(batch));
    }

    @Test
    void perObjectFailureIsIsolatedFromTheRestOfTheBatch() {
        stubValidEnvelope();
        when(reconciler.resolveIdentities(any(), any()))
                .thenReturn(new ConnectorIdentityResolution(Map.of(), Map.of()));
        ConnectorContentItem failing = content("C-fail");
        ConnectorContentItem healthy = content("C-ok");
        when(reconciler.reconcile(any(), eq(failing), any(), any()))
                .thenThrow(new IllegalArgumentException("grant references an unobserved principal: U-ghost"));
        when(reconciler.reconcile(any(), eq(healthy), any(), any()))
                .thenReturn(ObjectOutcome.MATERIALIZED);

        ConnectorCrawlBatch batch = batch(
                ConnectorContractVersions.supported(), "slack", List.of(failing, healthy), List.of());
        ConnectorIngestionResult result = service.ingest(batch);

        assertEquals(List.of("C-ok"), result.materialized());
        assertEquals(1, result.failures().size());
        assertEquals("C-fail", result.failures().getFirst().externalObjectId());
        assertTrue(result.failures().getFirst().reason().contains("unobserved principal"));
    }

    @Test
    void outcomesAndTombstonesAggregateIntoTheResult() {
        stubValidEnvelope();
        when(reconciler.resolveIdentities(any(), any()))
                .thenReturn(new ConnectorIdentityResolution(Map.of(), Map.of()));
        ConnectorContentItem fresh = content("C-new");
        ConnectorContentItem converged = content("C-existing");
        when(reconciler.reconcile(any(), eq(fresh), any(), any())).thenReturn(ObjectOutcome.MATERIALIZED);
        when(reconciler.reconcile(any(), eq(converged), any(), any())).thenReturn(ObjectOutcome.ROTATED);
        ConnectorTombstone gone = new ConnectorTombstone("C-gone");
        when(reconciler.retire(any(), eq(gone))).thenReturn(true);

        ConnectorCrawlBatch batch = batch(
                ConnectorContractVersions.supported(), "slack", List.of(fresh, converged), List.of(gone));
        ConnectorIngestionResult result = service.ingest(batch);

        assertEquals(List.of("C-new"), result.materialized());
        assertEquals(List.of("C-existing"), result.rotated());
        assertEquals(List.of("C-gone"), result.retired());
        assertTrue(result.failures().isEmpty());
    }

    private void stubValidEnvelope() {
        when(organizations.existsById(ORG)).thenReturn(true);
        AppUser actor = mock(AppUser.class);
        when(actor.isActive()).thenReturn(true);
        when(actor.getOrganizationId()).thenReturn(ORG);
        when(users.findById(ACTOR)).thenReturn(Optional.of(actor));
    }

    private static ConnectorContentItem content(String externalObjectId) {
        return new ConnectorContentItem(externalObjectId, "Title " + externalObjectId, "body", "r1");
    }

    private static ConnectorCrawlBatch batch(
            ConnectorContractVersions versions,
            String sourceSystem,
            List<ConnectorContentItem> contents,
            List<ConnectorTombstone> tombstones) {
        return new ConnectorCrawlBatch(
                ORG, sourceSystem, "T-workspace", SPACE, ACTOR, "cursor-1", versions,
                List.of(), contents, List.of(), tombstones);
    }
}
