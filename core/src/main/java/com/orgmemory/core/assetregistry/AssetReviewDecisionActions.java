package com.orgmemory.core.assetregistry;

record AssetReviewDecisionActions(
        boolean canApprove,
        boolean canRequestChanges,
        boolean canReject,
        boolean canCancel) {

    static AssetReviewDecisionActions none() {
        return new AssetReviewDecisionActions(false, false, false, false);
    }
}
