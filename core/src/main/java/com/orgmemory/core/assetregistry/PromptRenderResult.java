package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.UUID;

public record PromptRenderResult(
        UUID assetId,
        UUID releaseId,
        String releaseDigest,
        String systemInstruction,
        String userPrompt,
        List<String> sensitiveVariables,
        String inputShapeDigest) {

    public PromptRenderResult {
        sensitiveVariables = List.copyOf(sensitiveVariables);
    }
}
