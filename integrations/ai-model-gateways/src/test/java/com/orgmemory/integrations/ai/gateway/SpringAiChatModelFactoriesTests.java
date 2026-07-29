package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class SpringAiChatModelFactoriesTests {

    private static final SpringAiChatModelFactory.Request REQUEST =
            new SpringAiChatModelFactory.Request(
                    "https://provider.example/v1",
                    SecretValue.of("secret"),
                    "model-id",
                    Duration.ofSeconds(30));

    @Test
    void dispatchesToTheFactoryRegisteredForTheProtocol() {
        ChatModel openAi = mock(ChatModel.class);
        ChatModel anthropic = mock(ChatModel.class);
        var factories = new SpringAiChatModelFactories(List.of(
                factory(AiGatewayProtocol.OPENAI_COMPATIBLE, openAi),
                factory(AiGatewayProtocol.ANTHROPIC_MESSAGES, anthropic)));

        assertSame(
                openAi,
                factories.create(
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        REQUEST));
        assertSame(
                anthropic,
                factories.create(
                        AiGatewayProtocol.ANTHROPIC_MESSAGES,
                        REQUEST));
    }

    @Test
    void failsClosedWhenAProtocolFactoryIsMissing() {
        var factories = new SpringAiChatModelFactories(List.of(
                factory(
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        mock(ChatModel.class))));

        assertThrows(
                AiGatewayUnavailableException.class,
                () -> factories.create(
                        AiGatewayProtocol.ANTHROPIC_MESSAGES,
                        REQUEST));
    }

    @Test
    void rejectsDuplicateFactoriesForOneProtocol() {
        assertThrows(
                IllegalStateException.class,
                () -> new SpringAiChatModelFactories(List.of(
                        factory(
                                AiGatewayProtocol.OPENAI_COMPATIBLE,
                                mock(ChatModel.class)),
                        factory(
                                AiGatewayProtocol.OPENAI_COMPATIBLE,
                                mock(ChatModel.class)))));
    }

    private static SpringAiChatModelFactory factory(
            AiGatewayProtocol protocol,
            ChatModel model) {
        return new SpringAiChatModelFactory() {
            @Override
            public AiGatewayProtocol protocol() {
                return protocol;
            }

            @Override
            public ChatModel create(Request request) {
                return model;
            }
        };
    }
}
