package com.orgmemory.core.assetregistry;

public record PromptEvaluationComparison(
        PromptEvaluationResult baseline,
        PromptEvaluationResult candidate,
        int passedCaseDelta) {
}
