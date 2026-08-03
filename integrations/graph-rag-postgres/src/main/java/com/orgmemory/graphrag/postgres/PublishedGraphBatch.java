package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Objects;

/** Exact durable publication plus bounded relational topology measurements. */
record PublishedGraphBatch(
        ProjectionBatch batch,
        ProjectionSnapshot snapshot,
        long entityCount,
        long relationContributionCount,
        long unresolvedRelationCount,
        long unresolvedEndpointCount) {

    PublishedGraphBatch {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(snapshot, "snapshot");
        requireNonNegative(entityCount, "entityCount");
        requireNonNegative(relationContributionCount, "relationContributionCount");
        requireNonNegative(unresolvedRelationCount, "unresolvedRelationCount");
        requireNonNegative(unresolvedEndpointCount, "unresolvedEndpointCount");
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
