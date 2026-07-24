package com.orgmemory.core.knowledge;

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
import com.orgmemory.graphrag.port.GraphProjectionWriter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeAssetLifecycleServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();

    private final KnowledgeAssetRepository assets =
            mock(KnowledgeAssetRepository.class);
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final KnowledgeIngestionService ingestion =
            mock(KnowledgeIngestionService.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final GraphProjectionWriter graph =
            mock(GraphProjectionWriter.class);
    private final ModelInvocationCache modelCache =
            mock(ModelInvocationCache.class);
    private final RetrievalResultCache retrievalCache =
            mock(RetrievalResultCache.class);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");

    private final KnowledgeAssetLifecycleService service =
            new KnowledgeAssetLifecycleService(
                    assets,
                    versions,
                    ingestion,
                    authorization,
                    graph,
                    modelCache,
                    retrievalCache);

    @BeforeEach
    void setUpAsset() {
        KnowledgeAsset asset = mock(KnowledgeAsset.class);
        KnowledgeAssetVersion version = mock(KnowledgeAssetVersion.class);
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getCurrentVersionId()).thenReturn(VERSION_ID);
        when(asset.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(versions.findByIdAndOrganizationId(VERSION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(version));
        when(version.getSourceRevisionId()).thenReturn(REVISION_ID);
        when(ingestion.retire(ORGANIZATION_ID, ASSET_ID))
                .thenReturn(new KnowledgeAssetRef(
                        ASSET_ID,
                        VERSION_ID,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        KnowledgeAssetVersionStatus.RETIRED));
    }

    @Test
    void deleteRetiresCanonicalLedgerThenRemovesDerivedGraphAndCaches() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));

        service.delete(actor, ASSET_ID);

        verify(ingestion).retire(ORGANIZATION_ID, ASSET_ID);
        verify(graph).removeRevision(ORGANIZATION_ID, REVISION_ID);
        verify(modelCache).invalidate(any());
        verify(retrievalCache).invalidateNamespace(any());
    }

    @Test
    void deniedDeleteDoesNotMutateCanonicalOrDerivedState() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("DENIED", "model-v1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.delete(actor, ASSET_ID));

        verify(ingestion, never()).retire(any(), any());
        verify(graph, never()).removeRevision(any(), any());
    }
}
