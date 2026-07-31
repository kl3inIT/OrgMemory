package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.shared.error.BusinessConflictException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceGroupMembershipHeadTests {

    @Test
    void rejectsIncompleteAndNonIncreasingSnapshots() {
        UUID organizationId = UUID.randomUUID();
        UUID groupPrincipalId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        SourceGroupMembershipSnapshot generationTwo = snapshot(
                organizationId,
                groupPrincipalId,
                2,
                SourceMembershipCaptureStatus.COMPLETE,
                null,
                now);
        SourceGroupMembershipHead head = new SourceGroupMembershipHead(generationTwo, now);

        assertThrows(
                BusinessConflictException.class,
                () -> head.advance(generationTwo, now.plusSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> head.advance(
                        snapshot(
                                organizationId,
                                groupPrincipalId,
                                3,
                                SourceMembershipCaptureStatus.INCOMPLETE,
                                "UPSTREAM_PARTIAL",
                                now.plusSeconds(2)),
                        now.plusSeconds(2)));
    }

    private static SourceGroupMembershipSnapshot snapshot(
            UUID organizationId,
            UUID groupPrincipalId,
            long generation,
            SourceMembershipCaptureStatus captureStatus,
            String incompleteReason,
            Instant capturedAt) {
        return new SourceGroupMembershipSnapshot(
                organizationId,
                UUID.randomUUID(),
                groupPrincipalId,
                generation,
                captureStatus,
                incompleteReason,
                capturedAt);
    }
}
