package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.asset.KnowledgeAsset;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersion;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionStatus;

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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GraphIndexLifecycleServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    private final GraphIndexingCoordinator coordinator =
            mock(GraphIndexingCoordinator.class);
    private final GraphIndexJobQueue queue = mock(GraphIndexJobQueue.class);
    private final KnowledgeAssetRepository assets =
            mock(KnowledgeAssetRepository.class);
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");
    private final GraphIndexLifecycleService service =
            new GraphIndexLifecycleService(
                    coordinator, queue, assets, versions, authorization);

    @Test
    void ensureCurrentProfileAuthorizesAndEnqueuesTheActiveRevision() {
        KnowledgeAsset asset = mock(KnowledgeAsset.class);
        KnowledgeAssetVersion version = mock(KnowledgeAssetVersion.class);
        GraphIndexJobView expected = mock(GraphIndexJobView.class);
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getCurrentVersionId()).thenReturn(VERSION_ID);
        when(versions.findByIdAndOrganizationId(VERSION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(version));
        when(version.getStatus()).thenReturn(KnowledgeAssetVersionStatus.ACTIVE);
        when(version.getSourceRevisionId()).thenReturn(REVISION_ID);
        when(version.getId()).thenReturn(VERSION_ID);
        when(queue.enqueue(
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(JOB_ID);
        when(coordinator.status(ORGANIZATION_ID, JOB_ID)).thenReturn(expected);

        GraphIndexJobView actual = service.ensureCurrentProfile(actor, ASSET_ID);

        assertEquals(expected, actual);
        var reference = ArgumentCaptor.forClass(KnowledgeAssetRef.class);
        verify(queue).enqueue(
                org.mockito.ArgumentMatchers.eq(ORGANIZATION_ID),
                org.mockito.ArgumentMatchers.eq(REVISION_ID),
                reference.capture(),
                any(Instant.class));
        assertEquals(ASSET_ID, reference.getValue().knowledgeAssetId());
        assertEquals(VERSION_ID, reference.getValue().knowledgeAssetVersionId());
        assertEquals(
                KnowledgeAssetVersionStatus.ACTIVE,
                reference.getValue().status());
    }

    @Test
    void deniedRebuildDoesNotReadOrMutateGraphIndexState() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("DENIED", "model-v1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.ensureCurrentProfile(actor, ASSET_ID));

        verify(assets, never()).findByIdAndOrganizationId(any(), any());
        verify(queue, never()).enqueue(any(), any(), any(), any());
        verify(coordinator, never()).status(any(), any());
    }
}
