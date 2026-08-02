package com.orgmemory.core.assetregistry.prompt;

public record PromptEvaluationComparison(
        PromptEvaluationResult baseline,
        PromptEvaluationResult candidate,
        int passedCaseDelta) {
}
