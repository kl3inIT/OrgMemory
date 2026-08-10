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
    private final List<Entry> entries;

    AssetAuthorizationBatch(
            UUID organizationId,
            UUID assetId,
            UUID claimToken,
            List<UUID> outboxIds,
            List<Entry> entries) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.claimToken = Objects.requireNonNull(claimToken, "claimToken");
        this.outboxIds = List.copyOf(outboxIds);
        this.entries = List.copyOf(entries);
        if (this.outboxIds.size() != this.entries.size()) {
            throw new IllegalArgumentException("Outbox ids and entries must have equal size");
        }
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID assetId() {
        return assetId;
    }

    public List<RelationshipTuple> writes() {
        return tuples(AssetAuthorizationOperation.WRITE);
    }

    public List<RelationshipTuple> deletes() {
        return tuples(AssetAuthorizationOperation.DELETE);
    }

    UUID claimToken() {
        return claimToken;
    }

    List<UUID> outboxIds() {
        return outboxIds;
    }

    long generation() {
        return entries.stream().mapToLong(Entry::generation).max().orElseThrow();
    }

    private List<RelationshipTuple> tuples(AssetAuthorizationOperation operation) {
        return entries.stream()
                .filter(entry -> entry.operation() == operation)
                .map(Entry::tuple)
                .toList();
    }

    record Entry(
            AssetAuthorizationOperation operation,
            long generation,
            RelationshipTuple tuple) {

        Entry {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(tuple, "tuple");
        }
    }
}
