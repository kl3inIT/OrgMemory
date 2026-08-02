package com.orgmemory.integrations.ai.gateway.openai;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class OpenAiCompatibleChatModelFactory
        implements SpringAiChatModelFactory {

    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    OpenAiCompatibleChatModelFactory(
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<MeterRegistry> meters) {
        this.observations = observations.getIfAvailable(
                () -> ObservationRegistry.NOOP);
        this.meters = meters.getIfAvailable(
                io.micrometer.core.instrument.simple.SimpleMeterRegistry::new);
    }

    @Override
    public AiGatewayProtocol protocol() {
        return AiGatewayProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public ChatModel create(Request request) {
        var options = OpenAiChatOptions.builder()
                .baseUrl(request.baseUrl())
                .apiKey(request.credential().expose())
                .model(request.modelId())
                .timeout(request.timeout());
        if (request.openAiReasoningEffort() != null) {
            options.reasoningEffort(
                    request.openAiReasoningEffort().wireValue());
        }
        return OpenAiChatModel.builder()
                .options(options.build())
                .observationRegistry(observations)
                .meterRegistry(meters)
                .build();
    }
}
