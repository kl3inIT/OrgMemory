package com.orgmemory.core.assetregistry.promptcontract;

import com.orgmemory.core.ai.AiRoute;
import java.util.List;
import java.util.UUID;

public record PromptRunResult(
        UUID runId,
        UUID assetId,
        UUID releaseId,
        String releaseDigest,
        AiRoute modelRoute,
        String output,
        List<PromptCitation> citations,
        long durationMillis) {

    public PromptRunResult {
        citations = List.copyOf(citations);
    }

    public record PromptCitation(
            UUID chunkId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            String title,
            Integer startPage,
            Integer endPage,
            String heading) {
    }
}
