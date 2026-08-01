package com.orgmemory.core.knowledge.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceAclQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID RAW_SOURCE_OBJECT_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();

    private final SourceAclSnapshotRepository snapshots =
            mock(SourceAclSnapshotRepository.class);
    private final SourceAclQuery query = new SourceAclQuery(snapshots);

    @Test
    void exposesImmutableSnapshotFactsWithoutLeakingTheEntity() {
        SourceAclSnapshot snapshot = mock(SourceAclSnapshot.class);
        Instant capturedAt = Instant.parse("2026-08-01T00:00:00Z");
        when(snapshot.getId()).thenReturn(SNAPSHOT_ID);
        when(snapshot.getRawSourceObjectId()).thenReturn(RAW_SOURCE_OBJECT_ID);
        when(snapshot.getAclGeneration()).thenReturn(3L);
        when(snapshot.getCaptureStatus()).thenReturn(AclCaptureStatus.COMPLETE);
        when(snapshot.getCapturedAt()).thenReturn(capturedAt);
        when(snapshots.findByIdAndOrganizationId(SNAPSHOT_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(snapshot));

        SourceAclSnapshotRef result = query
                .findSnapshot(ORGANIZATION_ID, SNAPSHOT_ID)
                .orElseThrow();

        assertEquals(SNAPSHOT_ID, result.id());
        assertEquals(RAW_SOURCE_OBJECT_ID, result.rawSourceObjectId());
        assertEquals(3L, result.aclGeneration());
        assertEquals(AclCaptureStatus.COMPLETE, result.captureStatus());
        assertEquals(capturedAt, result.capturedAt());
    }

    @Test
    void mapsPersistenceProjectionsToAclOwnedSpaceGenerationFacts() {
        KnowledgeSpaceAclGenerationProjection projection =
                mock(KnowledgeSpaceAclGenerationProjection.class);
        when(projection.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(projection.getAclGeneration()).thenReturn(7L);
        when(snapshots.maximumCurrentAclGenerations(
                        ORGANIZATION_ID,
                        List.of(SNAPSHOT_ID)))
                .thenReturn(List.of(projection));

        assertEquals(
                List.of(new KnowledgeSpaceAclGenerationRef(SPACE_ID, 7L)),
                query.maximumCurrentAclGenerations(
                        ORGANIZATION_ID,
                        List.of(SNAPSHOT_ID)));
    }
}
