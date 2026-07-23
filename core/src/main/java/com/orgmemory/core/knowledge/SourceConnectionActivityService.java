package com.orgmemory.core.knowledge;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads back what a connection has done: how much of it is in the ledger, when it last got
 * through a batch, and what the recent attempts were.
 *
 * <p>Separate from {@link SourceConnectionAdminService} because it answers a different
 * question. That service governs a connection's configuration and holds the only path to a
 * stored credential; this one reads counters. Keeping them apart means a screen showing
 * activity never touches the class that can decrypt a token.
 */
@Service
public class SourceConnectionActivityService {

    private final SourceObjectRepository objects;
    private final ConnectorCrawlCheckpointService checkpoints;
    private final ConnectorCrawlAttemptService attempts;

    SourceConnectionActivityService(
            SourceObjectRepository objects,
            ConnectorCrawlCheckpointService checkpoints,
            ConnectorCrawlAttemptService attempts) {
        this.objects = objects;
        this.checkpoints = checkpoints;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public SourceConnectionActivityView describe(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        String system = require(sourceSystem, "sourceSystem");
        String key = require(sourceConnectionKey, "sourceConnectionKey");
        List<SourceObjectStatusCount> counts = objects.countByStatus(organizationId, system, key);
        return new SourceConnectionActivityView(
                system,
                key,
                countOf(counts, SourceObjectStatus.ACTIVE),
                countOf(counts, SourceObjectStatus.ARCHIVED),
                // Across statuses, because "when did anything last move here" does not care
                // whether the movement was an arrival or a retirement.
                counts.stream()
                        .map(SourceObjectStatusCount::lastUpdatedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null),
                checkpoints.lastCompletedAt(organizationId, system, key).orElse(null),
                attempts.recent(organizationId, system, key));
    }

    private static long countOf(List<SourceObjectStatusCount> counts, SourceObjectStatus status) {
        return counts.stream()
                .filter(count -> count.status() == status)
                .mapToLong(SourceObjectStatusCount::objects)
                .sum();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("connection " + field + " is required");
        }
        return value.trim();
    }
}
