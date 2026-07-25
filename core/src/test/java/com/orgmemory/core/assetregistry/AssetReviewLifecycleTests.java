package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetReviewLifecycleTests {

    @Test
    void reviewCaseResolvesOnlyOnceAndPinsTheRevisionDigest() {
        UUID organizationId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        AssetDraft draft = new AssetDraft(
                organizationId,
                UUID.randomUUID(),
                "Triage ticket",
                "Support workflow",
                "INTERNAL",
                "1",
                "{\"task\":\"triage\"}",
                authorId);
        AssetRevision revision = new AssetRevision(
                draft,
                1,
                "{\"task\":\"triage\"}",
                "a".repeat(64),
                "Initial review",
                authorId);
        AssetReviewCase review = new AssetReviewCase(
                revision, AssetRegistryCoordinator.REVIEW_POLICY_VERSION, authorId);

        review.decide(AssetReviewDecisionType.APPROVE, Instant.parse("2026-07-25T00:00:00Z"));

        assertEquals(AssetReviewState.APPROVED, review.getState());
        assertEquals(revision.getDigest(), review.getRevisionDigest());
        assertThrows(
                AssetConflictException.class,
                () -> review.decide(
                        AssetReviewDecisionType.REJECT,
                        Instant.parse("2026-07-25T00:01:00Z")));
    }
}
