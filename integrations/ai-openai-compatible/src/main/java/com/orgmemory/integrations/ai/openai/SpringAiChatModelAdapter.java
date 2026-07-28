package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.anthropic.models.messages.Model;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
final class SpringAiChatModelAdapter implements ChatModelPort {

    private final AiGatewayRegistry gateways;
    private final ObjectProvider<ChatMemory> memory;
    private final ObjectProvider<ChatClientBuilderConfigurer> clientConfigurer;
    private final ObservationRegistry observations;
    private final MeterRegistry meters;
    private final Map<ModelKey, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<ModelKey, ChatClient> clients = new ConcurrentHashMap<>();
    private final Map<ModelKey, ChatClient> memoryClients = new ConcurrentHashMap<>();

    SpringAiChatModelAdapter(
            AiGatewayRegistry gateways,
            ObjectProvider<ChatMemory> memory,
            ObjectProvider<ChatClientBuilderConfigurer> clientConfigurer,
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<MeterRegistry> meters) {
        this.gateways = gateways;
        this.memory = memory;
        this.clientConfigurer = clientConfigurer;
        this.observations = observations.getIfAvailable(
                () -> ObservationRegistry.NOOP);
        this.meters = meters.getIfAvailable(
                io.micrometer.core.instrument.simple.SimpleMeterRegistry::new);
    }

    @Override
    public Flux<String> stream(AiWorkload workload, ChatGenerationRequest request) {
        return Flux.defer(() -> {
            AiRoute route = gateways.resolve(workload);
            return stream(null, workload, route, request);
        });
    }

    @Override
    public Flux<String> stream(
            AiWorkload workload,
            AiRoute route,
            ChatGenerationRequest request) {
        return stream(null, workload, route, request);
    }

    @Override
    public Flux<String> stream(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            ChatGenerationRequest request) {
        if (workload.requiredCapability() != com.orgmemory.core.ai.AiGatewayCapability.CHAT) {
            return Flux.error(new IllegalArgumentException("ChatModelPort requires a CHAT workload"));
        }
        return Flux.defer(() -> {
            AiGatewayRegistry.ResolvedGateway gateway =
                    gateways.definition(organizationId, workload, route);
            return client(organizationId, workload, route, gateway)
                    .prompt()
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
        return stream(null, workload, request, conversationId);
    }

    @Override
    public Flux<String> stream(
            UUID organizationId,
            AiWorkload workload,
            ChatGenerationRequest request,
            String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return stream(organizationId, workload, request);
        }
        return Flux.defer(() -> {
            AiRoute route = organizationId == null
                    ? gateways.resolve(workload)
                    : gateways.resolve(organizationId, workload);
            AiGatewayRegistry.ResolvedGateway gateway =
                    gateways.definition(organizationId, workload, route);
            return memoryClient(
                            organizationId,
                            workload,
                            route,
                            gateway)
                    .prompt()
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

    @Override
    public Flux<String> stream(
            UUID organizationId,
            AiWorkload workload,
            ChatGenerationRequest request) {
        return Flux.defer(() -> {
            AiRoute route = gateways.resolve(organizationId, workload);
            return stream(organizationId, workload, route, request);
        });
    }

    private ChatClient client(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            AiGatewayRegistry.ResolvedGateway gateway) {
        ModelKey key = key(organizationId, workload, route, gateway);
        evictSuperseded(key);
        return clients.computeIfAbsent(
                key,
                ignored -> configuredBuilder(
                                model(
                                        organizationId,
                                        workload,
                                        route,
                                        gateway))
                        .build());
    }

    private ChatClient memoryClient(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            AiGatewayRegistry.ResolvedGateway gateway) {
        ModelKey key = key(organizationId, workload, route, gateway);
        evictSuperseded(key);
        return memoryClients.computeIfAbsent(key, ignored -> {
            ChatMemory chatMemory = memory.getIfAvailable();
            if (chatMemory == null) {
                throw new IllegalStateException(
                        "Conversation memory is not configured for assistant chat");
            }
            return configuredBuilder(model(
                            organizationId,
                            workload,
                            route,
                            gateway))
                    .defaultAdvisors(
                            MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();
        });
    }

    private ChatModel model(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            AiGatewayRegistry.ResolvedGateway gateway) {
        ModelKey key = key(organizationId, workload, route, gateway);
        return models.computeIfAbsent(key, ignored -> switch (gateway.protocol()) {
            case OPENAI_COMPATIBLE -> OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(gateway.baseUrl())
                            .apiKey(gateway.credential().expose())
                            .model(route.modelId())
                            .timeout(gateway.timeout())
                            .build())
                    .observationRegistry(observations)
                    .meterRegistry(meters)
                    .build();
            case ANTHROPIC_MESSAGES -> AnthropicChatModel.builder()
                    .options(AnthropicChatOptions.builder()
                            .baseUrl(gateway.baseUrl())
                            .apiKey(gateway.credential().expose())
                            .model(Model.of(route.modelId()))
                            .timeout(gateway.timeout())
                            .build())
                    .observationRegistry(observations)
                    .meterRegistry(meters)
                    .build();
        });
    }

    private ChatClient.Builder configuredBuilder(ChatModel model) {
        ChatClient.Builder builder = ChatClient.builder(model);
        ChatClientBuilderConfigurer configurer =
                clientConfigurer.getIfAvailable();
        return configurer == null
                ? builder
                : configurer.configure(builder);
    }

    private static ModelKey key(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            AiGatewayRegistry.ResolvedGateway gateway) {
        return new ModelKey(
                organizationId,
                workload,
                route,
                gateway.protocol(),
                gateway.profileVersion());
    }

    private void evictSuperseded(ModelKey active) {
        if (active.profileVersion() == 0) {
            return;
        }
        models.keySet().removeIf(candidate ->
                candidate.supersededBy(active));
        clients.keySet().removeIf(candidate ->
                candidate.supersededBy(active));
        memoryClients.keySet().removeIf(candidate ->
                candidate.supersededBy(active));
    }

    private record ModelKey(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            AiGatewayProtocol protocol,
            long profileVersion) {

        private boolean supersededBy(ModelKey active) {
            return organizationId.equals(active.organizationId)
                    && (workload == active.workload
                            || (route.gatewayId().equals(active.route.gatewayId())
                                    && profileVersion < active.profileVersion))
                    && !equals(active);
        }
    }
}
