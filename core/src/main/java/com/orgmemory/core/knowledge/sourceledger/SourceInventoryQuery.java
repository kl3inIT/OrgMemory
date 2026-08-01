package com.orgmemory.core.knowledge.sourceledger;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only boundary over Source Ledger's external-source inventory. */
@Service
@Transactional(readOnly = true)
public class SourceInventoryQuery {

    private final SourceObjectRepository sources;

    SourceInventoryQuery(SourceObjectRepository sources) {
        this.sources = sources;
    }

    public List<String> activeExternalObjectIds(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return sources.findActiveExternalObjectIds(
                Objects.requireNonNull(organizationId, "organizationId"),
                Objects.requireNonNull(sourceSystem, "sourceSystem"),
                Objects.requireNonNull(sourceConnectionKey, "sourceConnectionKey"));
    }

    public SourceInventorySummary summarize(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        List<SourceObjectStatusCount> counts = sources.countByStatus(
                Objects.requireNonNull(organizationId, "organizationId"),
                Objects.requireNonNull(sourceSystem, "sourceSystem"),
                Objects.requireNonNull(sourceConnectionKey, "sourceConnectionKey"));
        return new SourceInventorySummary(
                countOf(counts, SourceObjectStatus.ACTIVE),
                countOf(counts, SourceObjectStatus.ARCHIVED),
                counts.stream()
                        .map(SourceObjectStatusCount::lastUpdatedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null));
    }

    private static long countOf(
            List<SourceObjectStatusCount> counts, SourceObjectStatus status) {
        return counts.stream()
                .filter(count -> count.status() == status)
                .mapToLong(SourceObjectStatusCount::objects)
                .sum();
    }
}
