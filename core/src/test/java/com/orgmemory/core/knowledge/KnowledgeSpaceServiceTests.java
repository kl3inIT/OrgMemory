package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeSpaceServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SPACE_ID = UUID.fromString("88888888-8888-4888-8888-888888888802");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, DEPARTMENT_ID, "Linh Nguyen", "linh@example.com");

    private KnowledgeSpaceRepository spaces;
    private RelationshipAuthorizationPort authorization;
    private RelationshipAuthorizationSetPort authorizationSets;
    private KnowledgeSpaceService service;

    @BeforeEach
    void setUp() {
        spaces = mock(KnowledgeSpaceRepository.class);
        authorization = mock(RelationshipAuthorizationPort.class);
        authorizationSets = mock(RelationshipAuthorizationSetPort.class);
        service = new KnowledgeSpaceService(spaces, authorization, authorizationSets);
    }

    @Test
    void listsOnlyCanonicalSpacesResolvedByOpenFga() {
        ResourceRef resource = ResourceRef.of(ORGANIZATION_ID, "knowledge_space", SPACE_ID);
        KnowledgeSpace space = space();
        when(authorizationSets.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(List.of(resource), "model-1"));
        when(spaces.findByOrganizationIdAndIdInAndActiveTrueOrderByName(
                        ORGANIZATION_ID, Set.of(SPACE_ID)))
                .thenReturn(List.of(space));

        assertEquals(
                List.of(new KnowledgeSpaceTarget(SPACE_ID, "sales", "Sales Knowledge", DEPARTMENT_ID)),
                service.listUploadTargets(ACTOR));
    }

    @Test
    void failsClosedWhenOpenFgaCannotResolveUploadTargets() {
        when(authorizationSets.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.indeterminate("OPENFGA_UNAVAILABLE", "model-1"));

        assertThrows(KnowledgeSpaceUnavailableException.class, () -> service.listUploadTargets(ACTOR));
    }

    @Test
    void rejectsCrossTenantAuthorizationProjection() {
        ResourceRef wrongTenant = ResourceRef.of(UUID.randomUUID(), "knowledge_space", SPACE_ID);
        when(authorizationSets.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(List.of(wrongTenant), "model-1"));

        assertThrows(KnowledgeSpaceUnavailableException.class, () -> service.listUploadTargets(ACTOR));
    }

    @Test
    void deniesMutationWhenCreatePermissionIsMissing() {
        KnowledgeSpace space = space();
        when(spaces.findByIdAndOrganizationIdAndActiveTrue(SPACE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(space));
        when(authorization.check(any())).thenReturn(
                AuthorizationDecision.deny("RELATIONSHIP_DENIED", "model-1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.requireUploadTarget(ACTOR, SPACE_ID));
        verify(authorization).check(any());
    }

    private static KnowledgeSpace space() {
        KnowledgeSpace space = mock(KnowledgeSpace.class);
        when(space.getId()).thenReturn(SPACE_ID);
        when(space.getKey()).thenReturn("sales");
        when(space.getName()).thenReturn("Sales Knowledge");
        when(space.getDepartmentId()).thenReturn(DEPARTMENT_ID);
        return space;
    }
}
