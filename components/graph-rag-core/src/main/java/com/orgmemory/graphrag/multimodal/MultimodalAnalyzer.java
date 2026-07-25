package com.orgmemory.graphrag.multimodal;

/** Effect boundary implemented by Spring AI or another model runtime. */
public interface MultimodalAnalyzer {

    MultimodalAnalyzerRole role();

    MultimodalAnalysisOutcome analyze(MultimodalAnalysisRequest request);
}
