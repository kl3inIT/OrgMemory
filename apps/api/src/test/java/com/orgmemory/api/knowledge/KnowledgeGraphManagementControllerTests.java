package com.orgmemory.api.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.graph.KnowledgeGraphCurationCommand;
import com.orgmemory.core.knowledge.graph.KnowledgeGraphCurationService;
import com.orgmemory.core.knowledge.graph.KnowledgeGraphExportService;
import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

class KnowledgeGraphManagementControllerTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000004");
    private static final UUID REVISION_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000005");
    private static final UUID CHUNK_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000006");
    private static final UUID ACL_SNAPSHOT_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000007");
    private static final UUID ENTITY_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000008");

    @Test
    void derivesEvidenceOrganizationFromTheAuthenticatedActor() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "Curator",
                "curator@example.test");
        KnowledgeGraphCurationService curations =
                mock(KnowledgeGraphCurationService.class);
        KnowledgeGraphExportService exports =
                mock(KnowledgeGraphExportService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        when(actors.current(authentication)).thenReturn(actor);

        new KnowledgeGraphManagementController(curations, exports, actors)
                .curateEntity(
                        SPACE_ID,
                        new KnowledgeGraphManagementController.CurateEntityRequest(
                                "curation-1",
                                "Correct entity metadata",
                                7,
                                ENTITY_ID,
                                "Travel expense",
                                "process",
                                "Approved expense process",
                                new KnowledgeGraphManagementController.EvidenceRequest(
                                        ASSET_ID,
                                        REVISION_ID,
                                        CHUNK_ID,
                                        ACL_SNAPSHOT_ID,
                                        6)),
                        authentication);

        ArgumentCaptor<KnowledgeGraphCurationCommand> command =
                ArgumentCaptor.forClass(KnowledgeGraphCurationCommand.class);
        verify(curations).apply(eq(actor), command.capture());
        var entity =
                (KnowledgeGraphCurationCommand.CurateEntity) command.getValue();
        assertEquals(
                ORGANIZATION_ID,
                entity.governingEvidence().organizationId());
    }
}
