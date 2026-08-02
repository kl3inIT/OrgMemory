package com.orgmemory.core.assetregistry.promptcontract;

import com.orgmemory.core.organization.CurrentActor;
import java.util.Map;
import java.util.UUID;

/** Closed-world Prompt operations required by the in-app Assistant. */
public interface PromptAssistantOperations {

    PromptPreparationResult preparePrompt(
            CurrentActor actor, UUID assetId, UUID releaseId);

    PromptRenderResult renderPrompt(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables);

    PromptRunResult runPrompt(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables,
            String knowledgeQuery,
            String requestId);
}
