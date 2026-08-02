package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.organization.CurrentActor;
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
                new AssetPayloadDigester().canonicalize(
                        draft.getTitle(),
                        draft.getSummary(),
                        draft.getClassification(),
                        draft.getSchemaVersion(),
                        draft.getPayload()),
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

    @Test
    void reviewAffordancesUseTheSameActionSpecificDecisionPredicate() {
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
                new AssetPayloadDigester().canonicalize(
                        draft.getTitle(),
                        draft.getSummary(),
                        draft.getClassification(),
                        draft.getSchemaVersion(),
                        draft.getPayload()),
                "Initial review",
                authorId);
        AssetReviewCase review = new AssetReviewCase(
                revision,
                AssetRegistryCoordinator.REVIEW_POLICY_VERSION,
                authorId);
        CurrentActor author = actor(authorId, organizationId);
        CurrentActor reviewer = actor(UUID.randomUUID(), organizationId);

        AssetReviewDecisionActions authorActions =
                AssetRegistryCoordinator.reviewDecisionActions(
                        author,
                        review,
                        revision);
        AssetReviewDecisionActions reviewerActions =
                AssetRegistryCoordinator.reviewDecisionActions(
                        reviewer,
                        review,
                        revision);

        assertFalse(authorActions.canApprove());
        assertTrue(authorActions.canRequestChanges());
        assertTrue(authorActions.canReject());
        assertTrue(authorActions.canCancel());
        assertTrue(reviewerActions.canApprove());
        assertTrue(reviewerActions.canRequestChanges());
        assertTrue(reviewerActions.canReject());
        assertFalse(reviewerActions.canCancel());

        review.decide(
                AssetReviewDecisionType.REQUEST_CHANGES,
                Instant.parse("2026-07-25T00:00:00Z"));

        assertEquals(
                AssetReviewDecisionActions.none(),
                AssetRegistryCoordinator.reviewDecisionActions(
                        reviewer,
                        review,
                        revision));
    }

    private static CurrentActor actor(
            UUID userId,
            UUID organizationId) {
        return new CurrentActor(
                userId,
                organizationId,
                null,
                "Reviewer",
                "reviewer@example.test");
    }
}
