package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
final class SpringAiChatModelAdapter implements ChatModelPort {

    private final AiGatewayRegistry gateways;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    SpringAiChatModelAdapter(AiGatewayRegistry gateways) {
        this.gateways = gateways;
    }

    @Override
    public Flux<String> stream(AiWorkload workload, ChatGenerationRequest request) {
        if (workload.requiredCapability() != com.orgmemory.core.ai.AiGatewayCapability.CHAT) {
            return Flux.error(new IllegalArgumentException("ChatModelPort requires a CHAT workload"));
        }
        return Flux.defer(() -> {
            AiRoute route = gateways.resolve(workload);
            return client(route)
                    .prompt()
                    .options(OpenAiChatOptions.builder().model(route.modelId()))
                    .system(request.systemInstruction())
                    .user(request.userPrompt())
                    .stream()
                    .content();
        }).onErrorMap(
                error -> !(error instanceof AiGatewayUnavailableException),
                error -> new AiGatewayUnavailableException("The configured AI gateway is unavailable", error));
    }

    private ChatClient client(AiRoute route) {
        return clients.computeIfAbsent(route.gatewayId(), gatewayId ->
                ChatClient.builder(model(route)).build());
    }

    private ChatModel model(AiRoute route) {
        return models.computeIfAbsent(route.gatewayId(), gatewayId -> {
            AiGatewayProperties.Gateway gateway = gateways.definition(AiWorkload.ASSISTANT_CHAT, route);
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
