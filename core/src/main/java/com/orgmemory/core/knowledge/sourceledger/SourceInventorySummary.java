package com.orgmemory.core.knowledge.sourceledger;

import java.time.Instant;

/** Immutable Source Ledger inventory facts for one external-source connection. */
public record SourceInventorySummary(
        long activeObjects, long archivedObjects, Instant lastUpdatedAt) {}
