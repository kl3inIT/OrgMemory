package com.orgmemory.core.assetregistry.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.authorization.RelationshipTuple;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetAuthorizationOutboxTests {

    @Test
    void staleClaimsCannotCompleteAnotherWorkersLease() {
        AssetAuthorizationOutbox outbox = outbox();
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        UUID firstClaim = UUID.randomUUID();
        UUID secondClaim = UUID.randomUUID();
        outbox.claim(firstClaim, now, now.plusSeconds(30));
        outbox.claim(
                secondClaim,
                now.plusSeconds(30),
                now.plusSeconds(60));

        assertThrows(
                AssetConflictException.class,
                () -> outbox.markApplied(
                        firstClaim, "model-1", now.plusSeconds(31)));
        outbox.markApplied(secondClaim, "model-1", now.plusSeconds(31));

        assertEquals(AssetAuthorizationStatus.APPLIED, outbox.getStatus());
    }

    @Test
    void failuresBackOffAndEventuallyDeadLetter() {
        AssetAuthorizationOutbox outbox = outbox();
        Instant now = Instant.parse("2026-07-25T00:00:00Z");

        for (int attempt = 1; attempt <= AssetAuthorizationOutbox.MAX_ATTEMPTS; attempt++) {
            UUID claim = UUID.randomUUID();
            outbox.claim(claim, now, now.plusSeconds(30));
            outbox.recordFailure(claim, "TEMPORARY", "OpenFGA unavailable", now);
            if (attempt < AssetAuthorizationOutbox.MAX_ATTEMPTS) {
                assertEquals(AssetAuthorizationStatus.PENDING, outbox.getStatus());
                assertTrue(outbox.getNextAttemptAt().isAfter(now));
                now = outbox.getNextAttemptAt();
            }
        }

        assertEquals(AssetAuthorizationStatus.DEAD_LETTER, outbox.getStatus());
        assertEquals(AssetAuthorizationOutbox.MAX_ATTEMPTS, outbox.getAttemptCount());
    }

    private static AssetAuthorizationOutbox outbox() {
        UUID assetId = UUID.randomUUID();
        return new AssetAuthorizationOutbox(
                UUID.randomUUID(),
                assetId,
                null,
                AssetAuthorizationOperation.WRITE,
                1,
                RelationshipTuple.of(
                        "user:" + UUID.randomUUID(),
                        "owner",
                        "asset:" + assetId));
    }
}
