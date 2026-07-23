package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * A connection as an administrator sees it. There is no field for the credential itself and
 * never will be: the screen needs to know whether one is set and who set it, which is what
 * {@code credentialSet} answers, and nothing on this path has a reason to see the token.
 *
 * @param sourceConfig the source's own settings, as the JSON object they were stored as
 */
public record SourceConnectionConfigurationView(
        String sourceSystem,
        String sourceConnectionKey,
        SourceIdentityTrust identityTrust,
        boolean crawlEnabled,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        String sourceConfig,
        long contentCrawlIntervalSeconds,
        boolean credentialSet,
        UUID credentialSetByUserId,
        Instant credentialSetAt,
        UUID configuredByUserId,
        Instant configuredAt) {
}
