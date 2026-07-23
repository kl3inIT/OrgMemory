package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** Resolves a Spring AI chat model through the same provider-neutral route registry as the API. */
@Component
public final class OpenAiCompatibleChatModelProvider {

    private final AiGatewayRegistry gateways;
    private final Map<AiRoute, ChatModel> models = new ConcurrentHashMap<>();

    OpenAiCompatibleChatModelProvider(AiGatewayRegistry gateways) {
        this.gateways = gateways;
    }

    public ChatModel resolve(AiWorkload workload) {
        AiRoute route = gateways.resolve(workload);
        return models.computeIfAbsent(route, ignored -> {
            AiGatewayProperties.Gateway gateway = gateways.definition(workload, route);
            return OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(gateway.baseUrl())
                            .apiKey(gateway.apiKey())
                            .model(route.modelId())
                            .timeout(gateway.timeout())
                            .build())
                    .build();
        });
    }
}
