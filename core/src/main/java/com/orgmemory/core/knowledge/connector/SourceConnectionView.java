package com.orgmemory.core.knowledge.connector;

import com.orgmemory.core.knowledge.SourceIdentityTrust;

import java.time.Instant;
import java.util.UUID;

/**
 * One observed source connection with its standing trust decision and how much of
 * its identity surface is actually resolved. {@code unmappedUserCount} is the
 * governance number: that many observed people currently resolve to nobody, so
 * every grant addressed to them denies.
 */
public record SourceConnectionView(
        String sourceSystem,
        String sourceConnectionKey,
        SourceIdentityTrust identityTrust,
        UUID trustDecidedByUserId,
        Instant trustDecidedAt,
        int userCount,
        int mappedUserCount,
        int groupCount,
        Instant lastSeenAt) {

    public int unmappedUserCount() {
        return userCount - mappedUserCount;
    }
}
