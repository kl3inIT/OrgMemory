package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

/** Resolves a Spring AI chat model through the same provider-neutral route registry as the API. */
@Component
public final class OpenAiCompatibleChatModelProvider {

    private final AiGatewayRegistry gateways;
    private final ObservationRegistry observations;
    private final MeterRegistry meters;
    private final Map<ModelKey, ChatModel> models = new ConcurrentHashMap<>();

    OpenAiCompatibleChatModelProvider(
            AiGatewayRegistry gateways,
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<MeterRegistry> meters) {
        this.gateways = gateways;
        this.observations = observations.getIfAvailable(
                () -> ObservationRegistry.NOOP);
        this.meters = meters.getIfAvailable(
                io.micrometer.core.instrument.simple.SimpleMeterRegistry::new);
    }

    public ChatModel resolve(AiWorkload workload) {
        return resolve(workload, gateways.resolve(workload));
    }

    public ChatModel resolve(AiWorkload workload, AiRoute route) {
        AiGatewayRegistry.ResolvedGateway gateway =
                gateways.definition(null, workload, route);
        ModelKey key = new ModelKey(route, gateway.profileVersion());
        return models.computeIfAbsent(key, ignored -> {
            return OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(gateway.baseUrl())
                            .apiKey(gateway.credential().expose())
                            .model(route.modelId())
                            .timeout(gateway.timeout())
                            .build())
                    .observationRegistry(observations)
                    .meterRegistry(meters)
                    .build();
        });
    }

    private record ModelKey(AiRoute route, long profileVersion) {
    }
}
