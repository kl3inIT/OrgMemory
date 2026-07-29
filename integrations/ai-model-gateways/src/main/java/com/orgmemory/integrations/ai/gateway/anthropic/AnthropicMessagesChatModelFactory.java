package com.orgmemory.integrations.ai.gateway.anthropic;

import com.anthropic.models.messages.Model;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class AnthropicMessagesChatModelFactory
        implements SpringAiChatModelFactory {

    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    AnthropicMessagesChatModelFactory(
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<MeterRegistry> meters) {
        this.observations = observations.getIfAvailable(
                () -> ObservationRegistry.NOOP);
        this.meters = meters.getIfAvailable(
                io.micrometer.core.instrument.simple.SimpleMeterRegistry::new);
    }

    @Override
    public AiGatewayProtocol protocol() {
        return AiGatewayProtocol.ANTHROPIC_MESSAGES;
    }

    @Override
    public ChatModel create(Request request) {
        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .baseUrl(request.baseUrl())
                        .apiKey(request.credential().expose())
                        .model(Model.of(request.modelId()))
                        .timeout(request.timeout())
                        .build())
                .observationRegistry(observations)
                .meterRegistry(meters)
                .build();
    }
}
