package com.orgmemory.core.knowledge.connector;

import com.orgmemory.core.knowledge.sourceledger.SourceInventoryQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceInventorySummary;

import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.Comparator;
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

    private final SourceInventoryQuery inventory;
    private final ConnectorCrawlCheckpointService checkpoints;
    private final ConnectorCrawlAttemptService attempts;

    SourceConnectionActivityService(
            SourceInventoryQuery inventory,
            ConnectorCrawlCheckpointService checkpoints,
            ConnectorCrawlAttemptService attempts) {
        this.inventory = inventory;
        this.checkpoints = checkpoints;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public SourceConnectionActivityView describe(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        String system = require(sourceSystem, "sourceSystem");
        String key = require(sourceConnectionKey, "sourceConnectionKey");
        SourceInventorySummary summary = inventory.summarize(organizationId, system, key);
        var componentCheckpoints =
                checkpoints.describe(organizationId, system, key);
        return new SourceConnectionActivityView(
                system,
                key,
                summary.activeObjects(),
                summary.archivedObjects(),
                summary.lastUpdatedAt(),
                componentCheckpoints.stream()
                        .map(ConnectorComponentCheckpointView::observedAt)
                        .max(Comparator.naturalOrder())
                        .orElse(null),
                componentCheckpoints,
                attempts.recent(organizationId, system, key));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(
                    "connection.identifier-required",
                    "connection " + field + " is required");
        }
        return value.trim();
    }
}
