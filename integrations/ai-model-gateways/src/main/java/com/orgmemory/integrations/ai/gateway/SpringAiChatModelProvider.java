package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/** Resolves a Spring AI chat model through the same provider-neutral route registry as the API. */
@Component
public final class SpringAiChatModelProvider {

    private final AiGatewayRegistry gateways;
    private final SpringAiChatModelFactories factories;
    private final Map<ModelKey, ChatModel> models = new ConcurrentHashMap<>();

    SpringAiChatModelProvider(
            AiGatewayRegistry gateways,
            SpringAiChatModelFactories factories) {
        this.gateways = gateways;
        this.factories = factories;
    }

    public ChatModel resolve(AiWorkload workload) {
        return resolve(null, workload, gateways.resolve(workload));
    }

    public ChatModel resolve(AiWorkload workload, AiRoute route) {
        return resolve(null, workload, route);
    }

    ChatModel resolve(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route) {
        AiGatewayRegistry.ResolvedGateway gateway =
                gateways.definition(organizationId, workload, route);
        ModelKey key = new ModelKey(
                organizationId,
                workload,
                route,
                gateway.protocol(),
                gateway.profileVersion());
        evictSuperseded(key);
        return models.computeIfAbsent(
                key,
                ignored -> factories.create(
                        gateway.protocol(),
                        new SpringAiChatModelFactory.Request(
                                gateway.baseUrl(),
                                gateway.credential(),
                                route.modelId(),
                                gateway.timeout())));
    }

    private void evictSuperseded(ModelKey active) {
        if (active.profileVersion() == 0) {
            return;
        }
        models.keySet().removeIf(candidate -> candidate.supersededBy(active));
    }

    private record ModelKey(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            com.orgmemory.core.ai.AiGatewayProtocol protocol,
            long profileVersion) {

        private boolean supersededBy(ModelKey active) {
            return java.util.Objects.equals(organizationId, active.organizationId)
                    && (workload == active.workload
                            || (route.gatewayId().equals(active.route.gatewayId())
                                    && profileVersion < active.profileVersion))
                    && !equals(active);
        }
    }
}
