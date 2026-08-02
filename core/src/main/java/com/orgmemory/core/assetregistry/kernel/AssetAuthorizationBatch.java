package com.orgmemory.core.assetregistry.kernel;

import com.orgmemory.core.authorization.RelationshipTuple;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Opaque persisted-lease capability presented to the external projection flow. */
public final class AssetAuthorizationBatch {

    private final UUID organizationId;
    private final UUID assetId;
    private final UUID claimToken;
    private final List<UUID> outboxIds;
    private final List<RelationshipTuple> tuples;

    AssetAuthorizationBatch(
            UUID organizationId,
            UUID assetId,
            UUID claimToken,
            List<UUID> outboxIds,
            List<RelationshipTuple> tuples) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.claimToken = Objects.requireNonNull(claimToken, "claimToken");
        this.outboxIds = List.copyOf(outboxIds);
        this.tuples = List.copyOf(tuples);
        if (this.outboxIds.size() != this.tuples.size()) {
            throw new IllegalArgumentException("Outbox ids and tuples must have equal size");
        }
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID assetId() {
        return assetId;
    }

    public List<RelationshipTuple> tuples() {
        return tuples;
    }

    UUID claimToken() {
        return claimToken;
    }

    List<UUID> outboxIds() {
        return outboxIds;
    }
}
