package com.orgmemory.core.assetregistry;

import com.orgmemory.core.authorization.RelationshipTuple;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

record AssetAuthorizationBatch(
        UUID organizationId,
        UUID assetId,
        UUID claimToken,
        List<UUID> outboxIds,
        List<RelationshipTuple> tuples) {

    AssetAuthorizationBatch {
        organizationId = Objects.requireNonNull(organizationId, "organizationId");
        assetId = Objects.requireNonNull(assetId, "assetId");
        claimToken = Objects.requireNonNull(claimToken, "claimToken");
        outboxIds = List.copyOf(outboxIds);
        tuples = List.copyOf(tuples);
        if (outboxIds.size() != tuples.size()) {
            throw new IllegalArgumentException("Outbox ids and tuples must have equal size");
        }
    }
}
