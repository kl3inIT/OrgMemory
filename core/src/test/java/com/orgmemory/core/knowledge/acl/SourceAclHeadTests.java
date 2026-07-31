package com.orgmemory.core.knowledge.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.shared.error.BusinessConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceAclHeadTests {

    @Test
    void advancesOnlyForTheSameSourceIdentityAndANewerGeneration() {
        UUID organizationId = UUID.randomUUID();
        SourceAclTarget firstTarget = target(organizationId, UUID.randomUUID(), "document-1");
        SourceAclSnapshot generationOne = snapshot(organizationId, firstTarget.rawSourceObjectId(), 1);
        SourceAclHead head = new SourceAclHead(firstTarget, generationOne);

        SourceAclTarget nextTarget = target(organizationId, UUID.randomUUID(), "document-1");
        SourceAclSnapshot generationTwo = snapshot(organizationId, nextTarget.rawSourceObjectId(), 2);
        head.advance(nextTarget, generationTwo);

        assertEquals(nextTarget.rawSourceObjectId(), head.getCurrentRawSourceObjectId());
        assertEquals(generationTwo.getId(), head.getCurrentSnapshotId());
        assertEquals(2, head.getAclGeneration());

        BusinessConflictException conflict = assertThrows(
                BusinessConflictException.class,
                () -> head.advance(nextTarget, generationTwo));
        assertEquals("knowledge-ingestion.conflict", conflict.code());

        SourceAclTarget differentIdentity = target(
                organizationId,
                UUID.randomUUID(),
                "document-2");
        assertThrows(
                IllegalArgumentException.class,
                () -> head.advance(
                        differentIdentity,
                        snapshot(organizationId, differentIdentity.rawSourceObjectId(), 3)));
    }

    private static SourceAclTarget target(
            UUID organizationId,
            UUID rawSourceObjectId,
            String externalObjectId) {
        return new SourceAclTarget(
                rawSourceObjectId,
                organizationId,
                "google-drive",
                "connection-1",
                externalObjectId);
    }

    private static SourceAclSnapshot snapshot(
            UUID organizationId,
            UUID rawSourceObjectId,
            long generation) {
        Instant capturedAt = Instant.parse("2026-07-31T00:00:00Z");
        return new SourceAclSnapshot(
                organizationId,
                rawSourceObjectId,
                generation,
                AclCaptureStatus.COMPLETE,
                AccessGate.DENY,
                "acl-sha-256",
                capturedAt,
                capturedAt.plusSeconds(3600));
    }
}
