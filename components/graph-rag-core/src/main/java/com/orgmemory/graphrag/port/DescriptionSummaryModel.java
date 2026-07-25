package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.summarization.DescriptionSummaryRequest;

@FunctionalInterface
public interface DescriptionSummaryModel {

    String summarize(DescriptionSummaryRequest request);
}
