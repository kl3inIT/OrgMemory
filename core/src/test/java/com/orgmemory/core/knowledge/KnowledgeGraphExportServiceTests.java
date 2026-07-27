package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.export.GraphExportFormat;
import com.orgmemory.graphrag.export.GraphExportReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeGraphExportServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000004");

    private final KnowledgeSpaceRepository spaces =
            mock(KnowledgeSpaceRepository.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final KnowledgeEvidenceScopeResolver evidenceScopes =
            mock(KnowledgeEvidenceScopeResolver.class);
    private final GraphExportReader reader = mock(GraphExportReader.class);
    private final PermissionAuditService audit =
            mock(PermissionAuditService.class);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");
    private final KnowledgeGraphExportService service =
            new KnowledgeGraphExportService(
                    spaces,
                    authorization,
                    evidenceScopes,
                    reader,
                    audit);

    @BeforeEach
    void setUpEntryPermission() {
        when(spaces.existsByIdAndOrganizationIdAndActiveTrue(
                        SPACE_ID, ORGANIZATION_ID))
                .thenReturn(true);
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        when(evidenceScopes.resolve(actor, "model-v1")).thenReturn(
                new ResolvedKnowledgeEvidenceScope(
                        ORGANIZATION_ID,
                        USER_ID,
                        null,
                        false,
                        "model-v1",
                        Instant.parse("2026-07-24T00:00:00Z"),
                        Map.of(SPACE_ID, Set.of(ASSET_ID)),
                        Map.of(SPACE_ID, 9L)));
    }

    @Test
    void exportsOnlyTheCurrentAuthorizedEvidenceScopeAndAuditsEgress() {
        when(reader.read(any(), any()))
                .thenReturn(new GraphExportDocument(List.of(), List.of()));

        var artifact =
                service.export(actor, SPACE_ID, GraphExportFormat.JSON, "request-1");

        assertEquals("application/json", artifact.mediaType());
        ArgumentCaptor<AuthorizedEvidenceScope> scope =
                ArgumentCaptor.forClass(AuthorizedEvidenceScope.class);
        verify(reader).read(scope.capture(), any());
        assertEquals(ORGANIZATION_ID, scope.getValue().organizationId());
        assertEquals(USER_ID, scope.getValue().actorUserId());
        assertEquals(
                java.util.Set.of(ASSET_ID),
                scope.getValue().authorizedAssetIds());
        assertEquals("model-v1", scope.getValue().authorizationModelId());
        assertEquals(9L, scope.getValue().aclGeneration());
        verify(audit).record(any());
    }

    @Test
    void reportsUnavailableForUnexpectedOpenFgaObjectTypesBeforeReadingGraphData() {
        when(evidenceScopes.resolve(actor, "model-v1")).thenThrow(
                new KnowledgeEvidenceScopeUnavailableException(
                        "AUTHORIZED_OBJECT_SET_INVALID",
                        "model-v1"));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.export(
                        actor, SPACE_ID, GraphExportFormat.JSON, "request-1"));

        verify(reader, never()).read(any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void reportsUnavailableForAnAuthorizedObjectSetAboveTheConfiguredBound() {
        when(evidenceScopes.resolve(actor, "model-v1")).thenThrow(
                new KnowledgeEvidenceScopeUnavailableException(
                        "AUTHORIZED_OBJECT_SET_INVALID",
                        "model-v1"));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.export(
                        actor, SPACE_ID, GraphExportFormat.JSON, "request-1"));

        verify(reader, never()).read(any(), any());
    }
}
