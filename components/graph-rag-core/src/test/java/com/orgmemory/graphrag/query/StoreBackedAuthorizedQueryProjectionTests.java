package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.VectorIndex;
import com.orgmemory.graphrag.testkit.InMemoryContentStore;
import com.orgmemory.graphrag.testkit.InMemoryProjectionPublicationStore;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreBackedAuthorizedQueryProjectionTests {

    @Test
    void chunkKeepsAssetGenerationWhenPublicationSnapshotAdvances() {
        InMemoryProjectionPublicationStore publications =
                new InMemoryProjectionPublicationStore();
        InMemoryContentStore content = new InMemoryContentStore(publications);
        ProjectionNamespace namespace =
                new ProjectionNamespace(organizationId(), "default", "knowledge");
        ContentStore.ContentRecord record = record(Map.of(
                ContentStore.ASSET_PROJECTION_GENERATION_METADATA_KEY,
                "1"));

        ProjectionSnapshot snapshot = publish(content, publications, namespace, record);
        StoreBackedAuthorizedQueryProjection projection =
                new StoreBackedAuthorizedQueryProjection(
                        content,
                        unused(VectorIndex.class),
                        unused(GraphStore.class));

        AuthorizedQueryProjection.Chunk chunk = projection
                .loadChunks(scope(), snapshot, List.of(record.evidence().chunkId()))
                .getFirst();

        assertEquals(5, snapshot.generation());
        assertEquals(1, chunk.projectionGeneration());
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
        UUID assetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID revisionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID chunkId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID aclSnapshotId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        return new ContentStore.ContentRecord(
                chunkId.toString(),
                new EvidenceReference(
                        organizationId(),
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

    private static ProjectionSnapshot publish(
            InMemoryContentStore content,
            InMemoryProjectionPublicationStore publications,
            ProjectionNamespace namespace,
            ContentStore.ContentRecord record) {
        ProjectionSnapshot snapshot = null;
        for (long generation = 1; generation <= 5; generation++) {
            ProjectionBatch batch = new ProjectionBatch(
                    UUID.nameUUIDFromBytes(
                            ("batch-" + generation).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    namespace,
                    generation - 1,
                    generation,
                    "publication-" + generation,
                    "manifest-" + generation,
                    Set.of(ProjectionKind.CONTENT),
                    Instant.parse("2026-07-25T00:00:00Z").plusSeconds(generation));
            if (generation == 1) {
                content.stageUpsert(batch, List.of(record));
            } else {
                content.stageDelete(batch, List.of());
            }
            publications.markPrepared(
                    batch,
                    ProjectionKind.CONTENT,
                    Instant.parse("2026-07-25T00:01:00Z").plusSeconds(generation));
            snapshot = publications.publish(
                    batch,
                    new com.orgmemory.graphrag.storage.ProjectionCommitPermit(
                            UUID.nameUUIDFromBytes(
                                    ("permit-" + batch.id())
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            batch.id(),
                            batch.manifestFingerprint(),
                            1,
                            Instant.parse("2026-07-25T00:02:00Z")
                                    .plusSeconds(generation)),
                    Instant.parse("2026-07-25T00:02:00Z").plusSeconds(generation));
        }
        return snapshot;
    }

    private static AuthorizedEvidenceScope scope() {
        return new AuthorizedEvidenceScope(
                organizationId(),
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                null,
                false,
                Set.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                "model-v1",
                3,
                Instant.parse("2026-07-25T00:03:00Z"));
    }

    private static UUID organizationId() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> {
                    throw new AssertionError(
                            method.getName()
                                    + " on "
                                    + proxy.getClass().getInterfaces()[0].getSimpleName()
                                    + " must not be used by loadChunks; argument count="
                                    + (arguments == null ? 0 : arguments.length));
                }));
    }
}
