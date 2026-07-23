package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.model.ExtractionRequest;
import com.orgmemory.graphrag.model.ExtractionResult;

@FunctionalInterface
public interface EntityRelationExtractor {

    ExtractionResult extract(ExtractionRequest request);
}
