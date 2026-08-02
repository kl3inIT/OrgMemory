package com.orgmemory.core.assetregistry.prompt;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;
import com.orgmemory.core.assetregistry.promptcontract.PromptAssistantOperations;
import com.orgmemory.core.assetregistry.promptcontract.PromptPreparationResult;
import com.orgmemory.core.assetregistry.promptcontract.PromptRenderResult;
import com.orgmemory.core.assetregistry.promptcontract.PromptRunResult;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.organization.CurrentActor;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PromptAssistantOperationsAdapter implements PromptAssistantOperations {

    private final PromptPreparationService preparation;
    private final PromptExecutionService execution;

    PromptAssistantOperationsAdapter(
            PromptPreparationService preparation, PromptExecutionService execution) {
        this.preparation = preparation;
        this.execution = execution;
    }

    @Override
    public PromptPreparationResult preparePrompt(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        return preparation.preparePrompt(actor, assetId, releaseId);
    }

    @Override
    public PromptRenderResult renderPrompt(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables) {
        return execution.render(actor, assetId, releaseId, variables);
    }

    @Override
    public PromptRunResult runPrompt(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables,
            String knowledgeQuery,
            String requestId) {
        return execution.run(
                actor, assetId, releaseId, variables, knowledgeQuery, requestId);
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({
    PermissionAwareKnowledgeSearch.class,
    ChatModelPort.class,
    AiRouteResolver.class
})
class PromptExecutionConfiguration {

    @Bean
    PromptExecutionService promptExecutionService(
            AssetReleaseUseQuery releases,
            PromptTemplateRenderer renderer,
            PermissionAwareKnowledgeSearch knowledge,
            ChatModelPort chat,
            AiRouteResolver routes,
            PromptRunCoordinator runs) {
        return new PromptExecutionService(
                releases, renderer, knowledge, chat, routes, runs);
    }

    @Bean
    PromptAssistantOperations promptAssistantOperations(
            PromptPreparationService preparation, PromptExecutionService execution) {
        return new PromptAssistantOperationsAdapter(preparation, execution);
    }
}
