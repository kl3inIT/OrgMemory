package com.orgmemory.core.knowledge.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import com.orgmemory.graphrag.model.FloatVector;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeAssetGraphQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();

    private final KnowledgeAssetRepository assets = mock(KnowledgeAssetRepository.class);
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final KnowledgeChunkProjectionStore chunks =
            mock(KnowledgeChunkProjectionStore.class);
    private final KnowledgeAssetGraphQuery query =
            new KnowledgeAssetGraphQuery(assets, versions, chunks);

    @Test
    void exposesImmutableAssetVersionAndChunkFacts() {
        KnowledgeAsset asset = mock(KnowledgeAsset.class);
        KnowledgeAssetVersion version = mock(KnowledgeAssetVersion.class);
        FloatVector embedding = new FloatVector(new float[] {0.1f, 0.2f});
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getId()).thenReturn(ASSET_ID);
        when(asset.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(asset.getCurrentVersionId()).thenReturn(VERSION_ID);
        when(versions.findByIdAndOrganizationId(VERSION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(version));
        when(version.getId()).thenReturn(VERSION_ID);
        when(version.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(version.getSourceRevisionId()).thenReturn(REVISION_ID);
        when(version.getSourceAclSnapshotId()).thenReturn(ACL_SNAPSHOT_ID);
        when(version.getVersionNumber()).thenReturn(3L);
        when(version.getLanguage()).thenReturn("vi");
        when(version.getStatus()).thenReturn(KnowledgeAssetVersionStatus.ACTIVE);
        when(chunks.loadActive(
                        ORGANIZATION_ID,
                        REVISION_ID,
                        ASSET_ID,
                        VERSION_ID,
                        3L))
                .thenReturn(List.of(new KnowledgeChunkProjection(
                        CHUNK_ID, 0, "Governed knowledge", "Policy", 2, embedding)));

        KnowledgeAssetGraphRef assetRef =
                query.findAsset(ORGANIZATION_ID, ASSET_ID).orElseThrow();
        KnowledgeAssetVersionGraphRef versionRef =
                query.findVersion(ORGANIZATION_ID, VERSION_ID).orElseThrow();
        KnowledgeAssetGraphChunk chunk = query.loadActiveChunks(
                        ORGANIZATION_ID,
                        REVISION_ID,
                        ASSET_ID,
                        VERSION_ID,
                        3L)
                .getFirst();

        assertEquals(SPACE_ID, assetRef.knowledgeSpaceId());
        assertFalse(assetRef.archived());
        assertEquals(ACL_SNAPSHOT_ID, versionRef.sourceAclSnapshotId());
        assertTrue(versionRef.active());
        assertEquals(CHUNK_ID, chunk.id());
        assertEquals(embedding, chunk.embedding());
    }

    @Test
    void preservesOpaqueNotFoundBehaviorForMissingAndCrossSpaceAssets() {
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                KnowledgeAssetNotFoundException.class,
                () -> query.requireInSpace(ORGANIZATION_ID, ASSET_ID, SPACE_ID));

        KnowledgeAsset asset = mock(KnowledgeAsset.class);
        when(assets.findByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(asset.getId()).thenReturn(ASSET_ID);
        when(asset.getKnowledgeSpaceId()).thenReturn(UUID.randomUUID());

        assertThrows(
                KnowledgeResourceNotFoundException.class,
                () -> query.requireInSpace(ORGANIZATION_ID, ASSET_ID, SPACE_ID));
    }
}
