package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.List;

/**
 * What one connection has actually done, as opposed to what it was configured to do.
 *
 * <p>The pairing is deliberate: counts say what arrived, attempts say what happened trying. A
 * connection with objects and a recent failure is behind rather than broken; one with no
 * objects and a repeated {@code UNAVAILABLE} has never worked. Neither reading is available
 * from either half alone.
 */
public record SourceConnectionActivityView(
        String sourceSystem,
        String sourceConnectionKey,
        long objectsActive,
        long objectsArchived,
        Instant lastObjectAt,
        Instant lastCheckpointAt,
        List<ConnectorCrawlAttemptView> recentAttempts) {

    public SourceConnectionActivityView {
        recentAttempts = List.copyOf(recentAttempts);
    }

    public long objectsTotal() {
        return objectsActive + objectsArchived;
    }
}
