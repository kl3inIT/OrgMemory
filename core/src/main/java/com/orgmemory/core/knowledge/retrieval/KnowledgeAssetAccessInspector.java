package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.AccessState;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.UUID;

/** Bounded adapter query that rechecks relationship and canonical access for one Knowledge Asset. */
public interface KnowledgeAssetAccessInspector {

    AssetInspection inspectAsset(
            CurrentActor actor,
            UUID assetId,
            String authorizationModelId,
            Instant evaluatedAt);

    record AssetInspection(AccessState state, String reasonCode) {
    }
}
