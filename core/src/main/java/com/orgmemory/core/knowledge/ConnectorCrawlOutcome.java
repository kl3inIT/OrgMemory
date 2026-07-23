package com.orgmemory.core.knowledge;

/**
 * How a driver finished with one crawl batch.
 *
 * <p>The distinction that matters operationally is between {@link #REJECTED} and
 * {@link #FAILED}. A rejected batch was checkpointed past and will never be seen again, so
 * whatever it contained is lost until the source produces it differently; a failed batch is
 * still there and the next poll will try it. Reporting both as "error" would hide which of
 * those an administrator is looking at.
 */
public enum ConnectorCrawlOutcome {

    /** Reconciled. Individual objects inside it may still have failed. */
    SUCCEEDED,

    /** Refused for a reason no retry can change, and checkpointed past. */
    REJECTED,

    /** Left uncheckpointed for the next poll. */
    FAILED,

    /**
     * No batch was produced for this connection at all — the source could not be read. This is
     * what a revoked token, a missing credential or a withdrawn scope looks like from here, and
     * it is the one an administrator hits most: the screen says the crawl is enabled, and
     * nothing arrives, because the crawl never got as far as having a batch to fail at.
     */
    UNAVAILABLE
}
