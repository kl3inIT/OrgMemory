package com.orgmemory.core.knowledge;

import java.time.Instant;

/** One crawl attempt as an administrator reads it. */
public record ConnectorCrawlAttemptView(
        String crawlCursor,
        ConnectorCrawlOutcome outcome,
        int objectsMaterialized,
        int objectsRotated,
        int objectsRematerialized,
        int objectsRetired,
        int objectsFailed,
        String errorCode,
        String errorMessage,
        Instant attemptedAt) {

    /** Whether this attempt changed anything, which is not the same as whether it succeeded. */
    public boolean changedSomething() {
        return objectsMaterialized + objectsRotated + objectsRematerialized + objectsRetired > 0;
    }
}
