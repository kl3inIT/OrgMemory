package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.AccessState;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.UUID;

/** Bounded adapter query for one already relationship-authorized Knowledge Asset. */
public interface KnowledgeAssetAccessInspector {

    AssetInspection inspectAsset(
            CurrentActor actor,
            UUID assetId,
            String authorizationModelId,
            Instant evaluatedAt);

    record AssetInspection(AccessState state, String reasonCode) {
    }
}
