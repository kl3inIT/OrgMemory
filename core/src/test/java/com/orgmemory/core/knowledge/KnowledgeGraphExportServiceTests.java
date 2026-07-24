package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.export.GraphExportFormat;
import com.orgmemory.graphrag.export.GraphExportReader;
import java.util.List;
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
    private final KnowledgeAssetRepository assets =
            mock(KnowledgeAssetRepository.class);
    private final SourceAclSnapshotRepository aclSnapshots =
            mock(SourceAclSnapshotRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final RelationshipAuthorizationSetPort authorizationSets =
            mock(RelationshipAuthorizationSetPort.class);
    private final GraphExportReader reader = mock(GraphExportReader.class);
    private final PermissionAuditService audit =
            mock(PermissionAuditService.class);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");
    private final KnowledgeGraphExportService service =
            new KnowledgeGraphExportService(
                    spaces,
                    assets,
                    aclSnapshots,
                    users,
                    authorization,
                    authorizationSets,
                    reader,
                    audit,
                    new KnowledgeRetrievalProperties(20, 5, 2, 1_000));

    @BeforeEach
    void setUpEntryPermission() {
        AppUser user = mock(AppUser.class);
        when(users.findById(USER_ID)).thenReturn(java.util.Optional.of(user));
        when(user.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(UserRole.EMPLOYEE);
        when(spaces.existsByIdAndOrganizationIdAndActiveTrue(
                        SPACE_ID, ORGANIZATION_ID))
                .thenReturn(true);
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
    }

    @Test
    void exportsOnlyTheCurrentAuthorizedEvidenceScopeAndAuditsEgress() {
        when(authorizationSets.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                ORGANIZATION_ID, "knowledge_asset", ASSET_ID)),
                        "model-v1"));
        when(assets.findActiveIdsInKnowledgeSpace(
                        ORGANIZATION_ID, SPACE_ID, List.of(ASSET_ID)))
                .thenReturn(List.of(ASSET_ID));
        when(aclSnapshots.maximumCurrentAclGeneration(
                        eq(ORGANIZATION_ID), anyCollection()))
                .thenReturn(9L);
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
    void rejectsUnexpectedOpenFgaObjectTypesBeforeReadingGraphData() {
        when(authorizationSets.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.resolved(
                        List.of(new ResourceRef(
                                ORGANIZATION_ID, "knowledge_space", SPACE_ID.toString())),
                        "model-v1"));

        assertThrows(
                IllegalStateException.class,
                () -> service.export(
                        actor, SPACE_ID, GraphExportFormat.JSON, "request-1"));

        verify(reader, never()).read(any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void rejectsAnAuthorizedObjectSetAboveTheConfiguredBound() {
        UUID secondAsset =
                UUID.fromString("10000000-0000-0000-0000-000000000005");
        UUID thirdAsset =
                UUID.fromString("10000000-0000-0000-0000-000000000006");
        when(authorizationSets.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.resolved(
                        List.of(
                                ResourceRef.of(
                                        ORGANIZATION_ID,
                                        "knowledge_asset",
                                        ASSET_ID),
                                ResourceRef.of(
                                        ORGANIZATION_ID,
                                        "knowledge_asset",
                                        secondAsset),
                                ResourceRef.of(
                                        ORGANIZATION_ID,
                                        "knowledge_asset",
                                        thirdAsset)),
                        "model-v1"));

        assertThrows(
                IllegalStateException.class,
                () -> service.export(
                        actor, SPACE_ID, GraphExportFormat.JSON, "request-1"));

        verify(reader, never()).read(any(), any());
    }
}
