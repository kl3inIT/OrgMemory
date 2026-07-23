package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * What one pass over a source produced: the batches to ingest, and the connections that
 * produced nothing and why.
 *
 * <p>The second half is the point. A connection whose credential was revoked produces no
 * batch, so a driver that only sees batches sees an empty list and has nothing to record —
 * which is exactly the case an administrator is looking at the screen to understand. Losing
 * that to a log line meant the most common failure was the one the product could not explain.
 *
 * <p>Reporting a failure is not the same as stopping. A source with ten connections and one
 * revoked credential still crawls the nine.
 */
public record ConnectorPoll(
        List<ConnectorCrawlBatch> batches, List<ConnectorConnectionFailure> unavailable) {

    public ConnectorPoll {
        batches = List.copyOf(batches);
        unavailable = List.copyOf(unavailable);
    }

    /** For a source whose connections cannot individually fail — a directory of files, say. */
    public static ConnectorPoll of(List<ConnectorCrawlBatch> batches) {
        return new ConnectorPoll(batches, List.of());
    }
}
