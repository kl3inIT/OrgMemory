package com.orgmemory.worker.graph;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import com.orgmemory.integrations.ai.openai.OpenAiCompatibleChatModelProvider;
import com.orgmemory.integrations.graphrag.springai.SpringAiEntityRelationExtractor;
import org.springframework.stereotype.Component;

@Component
final class SpringAiGraphExtractorFactory implements GraphExtractorFactory {

    private final OpenAiCompatibleChatModelProvider chatModels;

    SpringAiGraphExtractorFactory(OpenAiCompatibleChatModelProvider chatModels) {
        this.chatModels = chatModels;
    }

    @Override
    public EntityRelationExtractor create(AiRoute route) {
        return new SpringAiEntityRelationExtractor(
                route.gatewayId(), chatModels.resolve(AiWorkload.GRAPH_EXTRACTION));
    }
}
