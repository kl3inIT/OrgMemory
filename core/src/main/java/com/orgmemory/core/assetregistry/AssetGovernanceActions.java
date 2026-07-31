package com.orgmemory.core.assetregistry;

/**
 * Actor-specific UI affordances for the governed Asset lifecycle.
 *
 * <p>These flags never replace authorization on mutation commands. They let a
 * client avoid presenting actions that the live permission authority will
 * reject.
 */
public record AssetGovernanceActions(
        boolean canEdit,
        boolean canSubmitReview,
        boolean canReview,
        boolean canPublish,
        boolean canPublishSkill,
        boolean canWithdraw) {
}
