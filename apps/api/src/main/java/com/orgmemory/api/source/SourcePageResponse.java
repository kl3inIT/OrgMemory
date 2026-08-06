package com.orgmemory.api.source;

import com.orgmemory.core.knowledge.sourceledger.SourceStatusCounts;
import com.orgmemory.core.knowledge.sourceledger.SourceSummaryPage;
import java.util.List;

record SourcePageResponse(
        List<SourceResponse> items,
        String nextCursor,
        int pageSize,
        long total,
        SourceStatusCountsResponse statusCounts) {

    static SourcePageResponse from(SourceSummaryPage page) {
        return new SourcePageResponse(
                page.items().stream().map(SourceResponse::from).toList(),
                page.nextCursor(),
                page.pageSize(),
                page.total(),
                SourceStatusCountsResponse.from(page.statusCounts()));
    }

    record SourceStatusCountsResponse(long processing, long ready, long attention) {

        static SourceStatusCountsResponse from(SourceStatusCounts counts) {
            return new SourceStatusCountsResponse(
                    counts.processing(), counts.ready(), counts.attention());
        }
    }
}
