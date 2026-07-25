package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ContentStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreBackedAuthorizedQueryProjectionTests {

    @Test
    void chunkKeepsAssetGenerationWhenPublicationSnapshotAdvances() {
        ContentStore.ContentRecord record = record(Map.of(
                ContentStore.ASSET_PROJECTION_GENERATION_METADATA_KEY,
                "4"));

        AuthorizedQueryProjection.Chunk chunk =
                StoreBackedAuthorizedQueryProjection.chunk(record);

        assertEquals(4, chunk.projectionGeneration());
    }

    @Test
    void chunkFailsClosedWhenAssetGenerationIsMissingOrInvalid() {
        assertThrows(
                IllegalStateException.class,
                () -> StoreBackedAuthorizedQueryProjection.chunk(record(Map.of())));
        assertThrows(
                IllegalStateException.class,
                () -> StoreBackedAuthorizedQueryProjection.chunk(record(Map.of(
                        ContentStore.ASSET_PROJECTION_GENERATION_METADATA_KEY,
                        "snapshot-5"))));
        assertThrows(
                IllegalStateException.class,
                () -> StoreBackedAuthorizedQueryProjection.chunk(record(Map.of(
                        ContentStore.ASSET_PROJECTION_GENERATION_METADATA_KEY,
                        "0"))));
    }

    private static ContentStore.ContentRecord record(Map<String, String> metadata) {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID assetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID revisionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID chunkId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID aclSnapshotId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        return new ContentStore.ContentRecord(
                chunkId.toString(),
                new EvidenceReference(
                        organizationId,
                        assetId,
                        revisionId,
                        chunkId,
                        aclSnapshotId,
                        3),
                ContentStore.ContentKind.CHUNK,
                "Full-time employees complete a 60-day probation period.",
                8,
                metadata);
    }
}
