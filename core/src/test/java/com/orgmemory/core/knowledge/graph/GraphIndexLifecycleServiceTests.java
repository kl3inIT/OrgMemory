package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef;

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
    private final KnowledgeAssetGraphQuery assets =
            mock(KnowledgeAssetGraphQuery.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");
    private final GraphIndexLifecycleService service =
            new GraphIndexLifecycleService(
                    coordinator, queue, assets, authorization);

    @Test
    void ensureCurrentProfileAuthorizesAndEnqueuesTheActiveRevision() {
        KnowledgeAssetGraphRef asset = new KnowledgeAssetGraphRef(
                ASSET_ID, UUID.randomUUID(), VERSION_ID, false);
        KnowledgeAssetVersionGraphRef version = new KnowledgeAssetVersionGraphRef(
                VERSION_ID,
                ASSET_ID,
                REVISION_ID,
                UUID.randomUUID(),
                1,
                "vi",
                true);
        GraphIndexJobView expected = mock(GraphIndexJobView.class);
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        when(assets.findAsset(ORGANIZATION_ID, ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(assets.findVersion(ORGANIZATION_ID, VERSION_ID))
                .thenReturn(Optional.of(version));
        when(queue.enqueue(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(JOB_ID);
        when(coordinator.status(ORGANIZATION_ID, JOB_ID)).thenReturn(expected);

        GraphIndexJobView actual = service.ensureCurrentProfile(actor, ASSET_ID);

        assertEquals(expected, actual);
        verify(queue).enqueue(
                org.mockito.ArgumentMatchers.eq(ORGANIZATION_ID),
                org.mockito.ArgumentMatchers.eq(REVISION_ID),
                org.mockito.ArgumentMatchers.eq(ASSET_ID),
                org.mockito.ArgumentMatchers.eq(VERSION_ID),
                any(Instant.class));
    }

    @Test
    void deniedRebuildDoesNotReadOrMutateGraphIndexState() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("DENIED", "model-v1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.ensureCurrentProfile(actor, ASSET_ID));

        verify(assets, never()).findAsset(any(), any());
        verify(queue, never()).enqueue(any(), any(), any(), any(), any());
        verify(coordinator, never()).status(any(), any());
    }
}
