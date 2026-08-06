package com.orgmemory.core.knowledge.sourceledger;

import java.util.List;
import java.util.Objects;

public record SourceSummaryPage(
        List<SourceSummary> items,
        String nextCursor,
        int pageSize,
        long total,
        SourceStatusCounts statusCounts) {

    public SourceSummaryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(statusCounts, "statusCounts");
    }

    static SourceSummaryPage empty(int pageSize) {
        return new SourceSummaryPage(
                List.of(), null, pageSize, 0, SourceStatusCounts.empty());
    }
}
