package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A connection as an administrator sees it. There is no field for the credential itself and
 * never will be: the screen needs to know whether one is set and who set it, which is what
 * {@code credentialSet} answers, and nothing on this path has a reason to see the token.
 */
public record SourceConnectionConfigurationView(
        String sourceSystem,
        String sourceConnectionKey,
        SourceIdentityTrust identityTrust,
        boolean crawlEnabled,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        List<String> channels,
        long contentCrawlIntervalSeconds,
        int maxThreadsPerChannel,
        boolean credentialSet,
        UUID credentialSetByUserId,
        Instant credentialSetAt,
        UUID configuredByUserId,
        Instant configuredAt) {

    public SourceConnectionConfigurationView {
        channels = channels == null ? List.of() : List.copyOf(channels);
    }
}
