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
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeGraphCurationServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    private final KnowledgeSpaceRepository spaces =
            mock(KnowledgeSpaceRepository.class);
    private final KnowledgeAssetRepository assets =
            mock(KnowledgeAssetRepository.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final GraphCurationStore store = mock(GraphCurationStore.class);
    private final ModelInvocationCache modelCache =
            mock(ModelInvocationCache.class);
    private final RetrievalResultCache retrievalCache =
            mock(RetrievalResultCache.class);
    private final KnowledgeGraphCurationService service =
            new KnowledgeGraphCurationService(
                    spaces,
                    assets,
                    authorization,
                    store,
                    modelCache,
                    retrievalCache);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");

    @BeforeEach
    void setUpSpaceAndEvidence() {
        when(spaces.existsByIdAndOrganizationIdAndActiveTrue(
                        SPACE_ID, ORGANIZATION_ID))
                .thenReturn(true);
        KnowledgeAsset asset = mock(KnowledgeAsset.class);
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(store.append(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void curatorCreatesGovernedContributionAndInvalidatesNamespace() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        var command = new KnowledgeGraphCurationCommand.CurateEntity(
                SPACE_ID,
                "curation-1",
                "correct entity description",
                7,
                ENTITY_ID,
                "Leave policy",
                "POLICY",
                "Approved policy",
                evidence());

        GraphCurationRecord result = service.apply(actor, command);

        var entity = (GraphCurationRecord.CuratedEntity) result;
        assertEquals(USER_ID, entity.provenance().actorUserId());
        assertEquals("model-v1", entity.provenance().authorizationModelId());
        assertEquals(evidence(), entity.governingEvidence());
        verify(modelCache).invalidate(entity.namespace());
        verify(retrievalCache).invalidateNamespace(entity.namespace());
    }

    @Test
    void unauthorizedCurationNeverReachesTheLedger() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("DENIED", "model-v1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.apply(
                        actor,
                        new KnowledgeGraphCurationCommand.CurateEntity(
                                SPACE_ID,
                                "curation-1",
                                "attempt",
                                7,
                                ENTITY_ID,
                                "Secret",
                                "POLICY",
                                "Denied",
                                evidence())));

        verify(store, never()).append(any(), any());
    }

    @Test
    void governingEvidenceCannotCrossKnowledgeSpaces() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(mock(KnowledgeAsset.class)));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(
                        actor,
                        new KnowledgeGraphCurationCommand.CurateEntity(
                                SPACE_ID,
                                "curation-1",
                                "attempt",
                                7,
                                ENTITY_ID,
                                "Secret",
                                "POLICY",
                                "Denied",
                                evidence())));
        verify(store, never()).append(any(), any());
    }

    private static EvidenceReference evidence() {
        return new EvidenceReference(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                CHUNK_ID,
                ACL_ID,
                7);
    }
}
