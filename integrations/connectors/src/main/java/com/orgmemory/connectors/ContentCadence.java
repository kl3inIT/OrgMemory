package com.orgmemory.connectors;

import com.orgmemory.core.knowledge.ConnectorCrawlConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When a connection is due to have its content re-read, as opposed to only its access re-checked.
 *
 * <p>Reading who may see a document is cheap; reading the document is not. So an adapter re-reads
 * access every poll and content far less often, and this is where "far less often" is decided.
 * Both the Slack and the Drive adapter make that same split, so the rule for it lives here once
 * rather than being written twice and drifting.
 *
 * <p>The schedule is held in memory on purpose. Losing it to a restart costs one extra content
 * crawl — the map is empty, so everything reads as due — and buys back not having a second
 * durable schedule to keep honest against the connection's own row.
 *
 * <p>An administrator can still ask for content out of turn, and that is the one input that does
 * cross from the database: {@link ConnectorCrawlConfiguration#contentCrawlRequestedAt()}. The
 * last request served is remembered here, so a newer request forces exactly one content crawl
 * and is then spent — a held request does not re-fire every poll, and a restart that forgets
 * having served it costs only the same extra crawl a restart already causes.
 */
public final class ContentCadence {

    private final Map<String, Instant> dueAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> servedRequest = new ConcurrentHashMap<>();

    /**
     * Whether this poll should read content. True when an unserved request is outstanding, or
     * when the interval since the last content crawl has elapsed.
     */
    public boolean contentDue(ConnectorCrawlConfiguration configuration, Instant now) {
        String key = key(configuration);
        return hasUnservedRequest(configuration, key)
                || !now.isBefore(dueAt.getOrDefault(key, Instant.EPOCH));
    }

    /**
     * Records that content was read now, so the next content crawl is an interval away and any
     * request that prompted this one is spent. Called only after the crawl produced a batch:
     * a content crawl that failed leaves both the schedule and the request as they were, so the
     * work is retried on the next poll rather than skipped until the interval comes round.
     */
    public void contentCrawled(ConnectorCrawlConfiguration configuration, Instant now) {
        String key = key(configuration);
        dueAt.put(key, now.plus(configuration.contentCrawlInterval()));
        if (configuration.contentCrawlRequestedAt() != null) {
            servedRequest.put(key, configuration.contentCrawlRequestedAt());
        }
    }

    private boolean hasUnservedRequest(ConnectorCrawlConfiguration configuration, String key) {
        Instant requested = configuration.contentCrawlRequestedAt();
        return requested != null && !requested.equals(servedRequest.get(key));
    }

    /** Two tenants may key a connection the same way, so the cadence is remembered per tenant. */
    private static String key(ConnectorCrawlConfiguration configuration) {
        return configuration.organizationId() + "/" + configuration.sourceConnectionKey();
    }
}
