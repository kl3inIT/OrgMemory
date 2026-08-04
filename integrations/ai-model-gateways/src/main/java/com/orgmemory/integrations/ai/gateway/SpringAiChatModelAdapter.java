package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.AssistantModelAuthorityService;
import com.orgmemory.core.ai.AssistantModelRouteAuthority;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
final class SpringAiChatModelAdapter implements ChatModelPort {

    private final AiGatewayRegistry gateways;
    private final SpringAiChatModelProvider chatModels;
    private final ObjectProvider<ChatMemory> memory;
    private final ObjectProvider<ChatClientBuilderConfigurer> clientConfigurer;
    private final AssistantModelAuthorityService assistantRoutes;
    private final PermissionAuditService audit;
    private final Map<ModelKey, ChatClient> clients = new ConcurrentHashMap<>();
    private final Map<ModelKey, ChatClient> memoryClients = new ConcurrentHashMap<>();

    SpringAiChatModelAdapter(
            AiGatewayRegistry gateways,
            SpringAiChatModelProvider chatModels,
            ObjectProvider<ChatMemory> memory,
            ObjectProvider<ChatClientBuilderConfigurer> clientConfigurer,
            AssistantModelAuthorityService assistantRoutes,
            PermissionAuditService audit) {
        this.gateways = gateways;
        this.chatModels = chatModels;
        this.memory = memory;
        this.clientConfigurer = clientConfigurer;
        this.assistantRoutes = assistantRoutes;
        this.audit = audit;
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

    @Override
    public Flux<String> stream(
            AssistantModelRouteAuthority authority,
            ChatGenerationRequest request,
            String conversationId,
            UUID actorUserId,
            String requestId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.error(new IllegalArgumentException(
                    "Assistant conversation identity is required"));
        }
        return Flux.defer(() -> {
            AiRoute route = assistantRoutes.revalidate(authority);
            audit.record(new PermissionAuditCommand(
                    authority.organizationId(),
                    actorUserId,
                    "ASSISTANT_MODEL_GENERATION",
                    "AI_MODEL_ROUTE",
                    route.gatewayId() + ":" + route.modelId(),
                    PermissionAuditDecision.ALLOW,
                    "EFFECTIVE_ROUTE_AUTHORIZED",
                    "assistant-model-authority-v1",
                    requestId,
                    null));
            AiGatewayRegistry.ResolvedGateway gateway = gateways.assistantDefinition(
                    authority.organizationId(),
                    route);
            return assistantMemoryClient(
                            authority.organizationId(),
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
                        "The selected Assistant model is unavailable", error));
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
                                chatModels.resolve(
                                        organizationId,
                                        workload,
                                        route))
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
            return configuredBuilder(chatModels.resolve(
                            organizationId,
                            workload,
                            route))
                    .defaultAdvisors(
                            MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();
        });
    }

    private ChatClient assistantMemoryClient(
            UUID organizationId,
            AiRoute route,
            AiGatewayRegistry.ResolvedGateway gateway) {
        ModelKey key = key(
                organizationId,
                AiWorkload.ASSISTANT_CHAT,
                route,
                gateway);
        evictSuperseded(key);
        return memoryClients.computeIfAbsent(key, ignored -> {
            ChatMemory chatMemory = memory.getIfAvailable();
            if (chatMemory == null) {
                throw new IllegalStateException(
                        "Conversation memory is not configured for assistant chat");
            }
            return configuredBuilder(chatModels.resolveAssistant(
                            organizationId,
                            route,
                            gateway))
                    .defaultAdvisors(
                            MessageChatMemoryAdvisor.builder(chatMemory).build())
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
        clients.keySet().removeIf(candidate ->
                candidate.supersededBy(active));
        memoryClients.keySet().removeIf(candidate ->
                candidate.supersededBy(active));
    }

}
