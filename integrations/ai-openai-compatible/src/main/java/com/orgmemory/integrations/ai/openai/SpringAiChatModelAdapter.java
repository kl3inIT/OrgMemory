package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
final class SpringAiChatModelAdapter implements ChatModelPort {

    private final AiGatewayRegistry gateways;
    private final ObjectProvider<ChatMemory> memory;
    private final Map<AiRoute, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<AiRoute, ChatClient> clients = new ConcurrentHashMap<>();
    private final Map<AiRoute, ChatClient> memoryClients = new ConcurrentHashMap<>();

    SpringAiChatModelAdapter(
            AiGatewayRegistry gateways,
            ObjectProvider<ChatMemory> memory) {
        this.gateways = gateways;
        this.memory = memory;
    }

    @Override
    public Flux<String> stream(AiWorkload workload, ChatGenerationRequest request) {
        return Flux.defer(() -> {
            AiRoute route = gateways.resolve(workload);
            return stream(workload, route, request);
        });
    }

    @Override
    public Flux<String> stream(
            AiWorkload workload,
            AiRoute route,
            ChatGenerationRequest request) {
        if (workload.requiredCapability() != com.orgmemory.core.ai.AiGatewayCapability.CHAT) {
            return Flux.error(new IllegalArgumentException("ChatModelPort requires a CHAT workload"));
        }
        return Flux.defer(() -> {
            gateways.definition(workload, route);
            return client(workload, route)
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

    @Override
    public Flux<String> stream(
            AiWorkload workload,
            ChatGenerationRequest request,
            String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return stream(workload, request);
        }
        return Flux.defer(() -> {
            AiRoute route = gateways.resolve(workload);
            gateways.definition(workload, route);
            return memoryClient(workload, route)
                    .prompt()
                    .options(OpenAiChatOptions.builder().model(route.modelId()))
                    .system(request.systemInstruction())
                    .user(request.userPrompt())
                    .advisors(advisors ->
                            advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content();
        }).onErrorMap(
                error -> !(error instanceof AiGatewayUnavailableException),
                error -> new AiGatewayUnavailableException(
                        "The configured AI gateway is unavailable", error));
    }

    private ChatClient client(AiWorkload workload, AiRoute route) {
        return clients.computeIfAbsent(
                route, ignored -> ChatClient.builder(model(workload, route)).build());
    }

    private ChatClient memoryClient(AiWorkload workload, AiRoute route) {
        return memoryClients.computeIfAbsent(route, ignored -> {
            ChatMemory chatMemory = memory.getIfAvailable();
            if (chatMemory == null) {
                throw new IllegalStateException(
                        "Conversation memory is not configured for assistant chat");
            }
            return ChatClient.builder(model(workload, route))
                    .defaultAdvisors(
                            MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();
        });
    }

    private ChatModel model(AiWorkload workload, AiRoute route) {
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
