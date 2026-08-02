package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier;
import com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException;
import com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeGraphExplorerServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID REVISION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID ACL_SNAPSHOT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID FIRST_ENTITY_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ENTITY_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_ENTITY_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID RELATION_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_RELATION_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_CHUNK_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_CHUNK_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000002");

    private final KnowledgeSpaceQuery spaces = mock(KnowledgeSpaceQuery.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final GraphEvidenceVerifier evidenceVerifier =
            mock(GraphEvidenceVerifier.class);
    private final GraphExportReader reader = mock(GraphExportReader.class);
    private final PermissionAuditService audit =
            mock(PermissionAuditService.class);
    private final CurrentActor actor =
            new CurrentActor(
                    USER_ID,
                    ORGANIZATION_ID,
                    null,
                    "User",
                    "user@example.com");
    private final GraphExplorerProperties properties =
            new GraphExplorerProperties(1, 10, 10, 100, 3, 8);
    private final KnowledgeGraphExplorerService service =
            new KnowledgeGraphExplorerService(
                    spaces,
                    authorization,
                    evidenceVerifier,
                    reader,
                    properties,
                    audit);

    @BeforeEach
    void setUpEntryPermission() {
        when(spaces.isActive(ORGANIZATION_ID, SPACE_ID))
                .thenReturn(true);
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        when(evidenceVerifier.verifyScope(actor, "model-v1"))
                .thenReturn(scope(Set.of(ASSET_ID), 9L));
        when(reader.read(any(), any())).thenReturn(document());
    }

    @Test
    void readsOnlyAuthorizedEvidenceAndReportsBoundedResults() {
        KnowledgeGraphView view =
                service.explore(actor, SPACE_ID, "", 1, 3, "request-1");

        assertEquals(1, view.entities().size());
        assertTrue(view.truncated());
        assertEquals(
                List.of(FIRST_CHUNK_ID, SECOND_CHUNK_ID),
                view.entities().getFirst().citationChunkIds());

        ArgumentCaptor<AuthorizedEvidenceScope> scope =
                ArgumentCaptor.forClass(AuthorizedEvidenceScope.class);
        verify(reader).read(scope.capture(), any());
        assertEquals(Set.of(ASSET_ID), scope.getValue().authorizedAssetIds());
        assertEquals("model-v1", scope.getValue().authorizationModelId());
        assertEquals(9L, scope.getValue().aclGeneration());
        verify(audit, times(2)).record(any());
    }

    @Test
    void searchesEntityAndRelationTextWithoutReadingAnotherScope() {
        KnowledgeGraphView view = service.explore(
                actor,
                SPACE_ID,
                "approves",
                10,
                3,
                "request-2");

        assertEquals(2, view.entities().size());
        assertEquals(1, view.relations().size());
        assertEquals("APPROVES", view.relations().getFirst().type());
        verify(reader).read(any(), any());
    }

    @Test
    void expandsSearchResultsOnlyToTheRequestedDepth() {
        when(reader.read(any(), any())).thenReturn(chainDocument());

        KnowledgeGraphView oneHop = service.explore(
                actor,
                SPACE_ID,
                "reimbursement",
                10,
                1,
                "request-depth-1");
        KnowledgeGraphView twoHops = service.explore(
                actor,
                SPACE_ID,
                "reimbursement",
                10,
                2,
                "request-depth-2");

        assertEquals(2, oneHop.entities().size());
        assertEquals(1, oneHop.relations().size());
        assertEquals(3, twoHops.entities().size());
        assertEquals(2, twoHops.relations().size());
    }

    @Test
    void doesNotExposeCurationEvidenceToOrdinaryViewers() {
        when(authorization.check(any()))
                .thenReturn(
                        AuthorizationDecision.allow("model-v1"),
                        AuthorizationDecision.deny(
                                "RELATIONSHIP_DENIED",
                                "model-v1"));

        KnowledgeGraphView view = service.explore(
                actor,
                SPACE_ID,
                "",
                10,
                3,
                "request-viewer");

        assertFalse(view.canCurate());
        assertNull(view.entities().getFirst().governingEvidence());
        assertNull(view.relations().getFirst().governingEvidence());
        ArgumentCaptor<PermissionAuditCommand> auditCommands =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(audit, times(2)).record(auditCommands.capture());
        PermissionAuditCommand curationAudit = auditCommands.getAllValues().stream()
                .filter(command -> command.operation().equals("CHECK_GRAPH_CURATION"))
                .findFirst()
                .orElseThrow();
        assertEquals(PermissionAuditDecision.DENY, curationAudit.decision());
        assertEquals("RELATIONSHIP_DENIED", curationAudit.reasonCode());
    }

    @Test
    void deniesBeforeReadingWhenTheSpaceIsNotAuthorized() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny(
                        "RELATIONSHIP_DENIED",
                        "model-v1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.explore(
                        actor, SPACE_ID, "", 10, 3, "request-3"));

        verify(reader, never()).read(any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void failsClosedWhenAuthorizationKeepsChangingDuringRead() {
        when(evidenceVerifier.verifyScope(actor, "model-v1"))
                .thenReturn(
                        scope(Set.of(ASSET_ID), 9L),
                        scope(Set.of(ASSET_ID), 10L),
                        scope(Set.of(ASSET_ID), 10L),
                        scope(Set.of(ASSET_ID), 11L));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.explore(
                        actor, SPACE_ID, "", 10, 3, "request-4"));

        verify(reader, org.mockito.Mockito.times(2)).read(any(), any());
        verify(audit, never()).record(any());
    }

    private static VerifiedGraphEvidenceScope scope(
            Set<UUID> assetIds,
            long generation) {
        return new VerifiedGraphEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-v1",
                Instant.parse("2026-07-24T00:00:00Z"),
                Map.of(SPACE_ID, assetIds),
                Map.of(SPACE_ID, generation));
    }

    private static GraphExportDocument document() {
        EvidenceReference firstEvidence =
                evidence(FIRST_CHUNK_ID);
        EvidenceReference secondEvidence =
                evidence(SECOND_CHUNK_ID);
        return new GraphExportDocument(
                List.of(
                        new GraphExportDocument.EntityRow(
                                FIRST_ENTITY_ID,
                                "Expense claim",
                                "PROCESS",
                                "Employee expense reimbursement workflow",
                                List.of(firstEvidence, secondEvidence)),
                        new GraphExportDocument.EntityRow(
                                SECOND_ENTITY_ID,
                                "Finance manager",
                                "ROLE",
                                "Approves expense claims",
                                List.of(secondEvidence))),
                List.of(new GraphExportDocument.RelationRow(
                        RELATION_ID,
                        SECOND_ENTITY_ID,
                        FIRST_ENTITY_ID,
                        "APPROVES",
                        List.of("finance", "approval"),
                        "Finance manager approves the expense claim",
                        1.0,
                        List.of(secondEvidence))));
    }

    private static GraphExportDocument chainDocument() {
        EvidenceReference evidence = evidence(FIRST_CHUNK_ID);
        return new GraphExportDocument(
                List.of(
                        new GraphExportDocument.EntityRow(
                                FIRST_ENTITY_ID,
                                "Expense claim",
                                "PROCESS",
                                "Employee expense reimbursement workflow",
                                List.of(evidence)),
                        new GraphExportDocument.EntityRow(
                                SECOND_ENTITY_ID,
                                "Finance manager",
                                "ROLE",
                                "Approves expense claims",
                                List.of(evidence)),
                        new GraphExportDocument.EntityRow(
                                THIRD_ENTITY_ID,
                                "Finance director",
                                "ROLE",
                                "Leads finance operations",
                                List.of(evidence))),
                List.of(
                        new GraphExportDocument.RelationRow(
                                RELATION_ID,
                                SECOND_ENTITY_ID,
                                FIRST_ENTITY_ID,
                                "APPROVES",
                                List.of("finance", "approval"),
                                "Finance manager approves the expense claim",
                                1.0,
                                List.of(evidence)),
                        new GraphExportDocument.RelationRow(
                                SECOND_RELATION_ID,
                                THIRD_ENTITY_ID,
                                SECOND_ENTITY_ID,
                                "MANAGES",
                                List.of("finance", "management"),
                                "Finance director manages the finance manager",
                                1.0,
                                List.of(evidence))));
    }

    private static EvidenceReference evidence(UUID chunkId) {
        return new EvidenceReference(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                chunkId,
                ACL_SNAPSHOT_ID,
                9L);
    }
}
