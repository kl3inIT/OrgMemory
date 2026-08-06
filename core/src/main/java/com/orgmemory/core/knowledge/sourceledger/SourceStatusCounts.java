package com.orgmemory.core.knowledge.sourceledger;

public record SourceStatusCounts(long processing, long ready, long attention) {

    static SourceStatusCounts from(SourceObjectRepository.SourceListingCountProjection projection) {
        return new SourceStatusCounts(
                projection.getProcessing(),
                projection.getReady(),
                projection.getAttention());
    }

    static SourceStatusCounts empty() {
        return new SourceStatusCounts(0, 0, 0);
    }
}
