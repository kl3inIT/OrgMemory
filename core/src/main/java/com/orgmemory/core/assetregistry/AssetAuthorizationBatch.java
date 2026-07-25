package com.orgmemory.core.assetregistry;

import com.orgmemory.core.authorization.RelationshipTuple;
import java.util.List;
import java.util.UUID;

record AssetAuthorizationBatch(
        UUID organizationId,
        UUID assetId,
        List<UUID> outboxIds,
        List<RelationshipTuple> tuples) {

    AssetAuthorizationBatch {
        outboxIds = List.copyOf(outboxIds);
        tuples = List.copyOf(tuples);
    }
}
