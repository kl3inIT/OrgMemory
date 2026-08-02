package com.orgmemory.core.assetregistry.prompt;

import java.util.List;
import java.util.UUID;

public record PromptEvaluationResult(
        UUID evaluationId,
        UUID assetId,
        UUID releaseId,
        String releaseDigest,
        int passedCases,
        int totalCases,
        List<CaseResult> cases) {

    public PromptEvaluationResult {
        cases = List.copyOf(cases);
    }

    public boolean passed() {
        return passedCases == totalCases;
    }

    public record CaseResult(
            String name,
            boolean passed,
            List<String> failedAssertions,
            UUID promptRunId) {

        public CaseResult {
            failedAssertions = List.copyOf(failedAssertions);
        }
    }
}
