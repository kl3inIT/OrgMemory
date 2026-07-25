package com.orgmemory.graphrag.multimodal;

import java.util.Objects;

/** Complete, immutable input to one modality-specific model call. */
public record MultimodalAnalysisRequest(
        MultimodalEvidenceScope evidenceScope,
        MultimodalSidecarItem item,
        MultimodalSurroundingContext surroundingContext,
        MultimodalAnalysisRoute route,
        MultimodalAnalysisCacheKey cacheKey) {

    public MultimodalAnalysisRequest {
        Objects.requireNonNull(evidenceScope, "evidenceScope");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(surroundingContext, "surroundingContext");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(cacheKey, "cacheKey");
    }
}
