package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.extraction.ExtractionRoundRequest;
import com.orgmemory.graphrag.extraction.ExtractionRoundResponse;

@FunctionalInterface
public interface ExtractionModel {

    ExtractionRoundResponse extract(ExtractionRoundRequest request);
}
