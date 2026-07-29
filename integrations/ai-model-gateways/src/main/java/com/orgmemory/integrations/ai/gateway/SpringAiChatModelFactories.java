package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiGatewayUnavailableException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
final class SpringAiChatModelFactories {

    private final Map<AiGatewayProtocol, SpringAiChatModelFactory> factories;

    SpringAiChatModelFactories(List<SpringAiChatModelFactory> factories) {
        EnumMap<AiGatewayProtocol, SpringAiChatModelFactory> indexed =
                new EnumMap<>(AiGatewayProtocol.class);
        for (SpringAiChatModelFactory factory : factories) {
            SpringAiChatModelFactory duplicate =
                    indexed.putIfAbsent(factory.protocol(), factory);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple Spring AI chat model factories are registered for "
                                + factory.protocol());
            }
        }
        this.factories = Map.copyOf(indexed);
    }

    ChatModel create(
            AiGatewayProtocol protocol,
            SpringAiChatModelFactory.Request request) {
        SpringAiChatModelFactory factory = factories.get(protocol);
        if (factory == null) {
            throw new AiGatewayUnavailableException(
                    "No Spring AI chat model factory is registered for " + protocol);
        }
        return factory.create(request);
    }
}
