package com.orgmemory.core.knowledge.asset;

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
import com.orgmemory.core.knowledge.sourceledger.SourceLifecycleService;
import com.orgmemory.core.knowledge.sourceledger.ReadyManualUploadRef;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeAssetLifecycleServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();

    private final KnowledgeAssetRepository assets =
            mock(KnowledgeAssetRepository.class);
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final ModelInvocationCache modelCache =
            mock(ModelInvocationCache.class);
    private final RetrievalResultCache retrievalCache =
            mock(RetrievalResultCache.class);
    private final SourceLifecycleService sourceLifecycle =
            mock(SourceLifecycleService.class);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");
    private final KnowledgeAsset asset = mock(KnowledgeAsset.class);
    private final KnowledgeAssetVersion version = mock(KnowledgeAssetVersion.class);

    private final KnowledgeAssetLifecycleService service =
            new KnowledgeAssetLifecycleService(
                    assets,
                    versions,
                    authorization,
                    modelCache,
                    retrievalCache,
                    sourceLifecycle);

    @BeforeEach
    void setUpAsset() {
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getCurrentVersionId()).thenReturn(VERSION_ID);
        when(asset.getArchivedAt()).thenReturn(null);
        when(asset.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(asset.getId()).thenReturn(ASSET_ID);
        when(versions.findByIdAndOrganizationId(VERSION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(version));
        when(version.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(version.getId()).thenReturn(VERSION_ID);
        when(version.getStatus()).thenReturn(KnowledgeAssetVersionStatus.RETIRED);
    }

    @Test
    void deleteRetiresAssetThenInvalidatesCaches() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));

        service.delete(actor, ASSET_ID);

        verify(version).retire(any());
        verify(asset).archive(any());
        verify(versions).save(version);
        verify(assets).save(asset);
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

        verify(version, never()).retire(any());
        verify(assets, never()).save(any());
    }

    @Test
    void deleteReadyUploadResolvesTheSourceThenRetiresBothAggregates() {
        UUID sourceId = UUID.randomUUID();
        when(sourceLifecycle.requireReadyManualUpload(
                        ORGANIZATION_ID, sourceId))
                .thenReturn(new ReadyManualUploadRef(
                        sourceId, ASSET_ID, VERSION_ID, false));
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));

        service.deleteSource(actor, sourceId);

        verify(sourceLifecycle).archiveReadyManualUpload(
                ORGANIZATION_ID, sourceId, ASSET_ID);
        verify(version).retire(any());
        verify(asset).archive(any());
    }

    @Test
    void repeatedSourceDeleteReturnsTheExistingRetirementWithoutMutatingAgain() {
        UUID sourceId = UUID.randomUUID();
        when(sourceLifecycle.requireReadyManualUpload(ORGANIZATION_ID, sourceId))
                .thenReturn(new ReadyManualUploadRef(
                        sourceId, ASSET_ID, VERSION_ID, true));
        when(asset.getArchivedAt()).thenReturn(java.time.Instant.now());
        when(version.getStatus()).thenReturn(KnowledgeAssetVersionStatus.RETIRED);
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));

        service.deleteSource(actor, sourceId);

        verify(version, never()).retire(any());
        verify(asset, never()).archive(any());
        verify(sourceLifecycle).archiveReadyManualUpload(
                ORGANIZATION_ID, sourceId, ASSET_ID);
    }

    @Test
    void permissionRevokedAfterTheListUsesTheOpaqueSourceNotFoundContract() {
        UUID sourceId = UUID.randomUUID();
        when(sourceLifecycle.requireReadyManualUpload(ORGANIZATION_ID, sourceId))
                .thenReturn(new ReadyManualUploadRef(
                        sourceId, ASSET_ID, VERSION_ID, false));
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("DENIED", "model-v1"));

        assertThrows(
                KnowledgeResourceNotFoundException.class,
                () -> service.deleteSource(actor, sourceId));

        verify(version, never()).retire(any());
        verify(sourceLifecycle, never()).archiveReadyManualUpload(any(), any(), any());
    }
}
