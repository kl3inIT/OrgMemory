package com.orgmemory.core.knowledge.sourceledger;

import java.time.Instant;

/** How many objects a connection holds in one status, and when the newest of them last moved. */
public record SourceObjectStatusCount(SourceObjectStatus status, long objects, Instant lastUpdatedAt) {
}
