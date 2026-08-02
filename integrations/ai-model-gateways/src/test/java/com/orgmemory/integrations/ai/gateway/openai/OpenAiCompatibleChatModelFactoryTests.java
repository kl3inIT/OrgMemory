package com.orgmemory.integrations.ai.gateway.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.OpenAiReasoningEffort;
import com.orgmemory.core.shared.secret.SecretValue;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;

@SuppressWarnings("unchecked")
class OpenAiCompatibleChatModelFactoryTests {

    @Test
    void passesTheDeclaredReasoningEffortToSpringAiOptions() {
        ObjectProvider<ObservationRegistry> observations = mock(ObjectProvider.class);
        ObjectProvider<MeterRegistry> meters = mock(ObjectProvider.class);
        when(observations.getIfAvailable(any(Supplier.class)))
                .thenReturn(ObservationRegistry.NOOP);
        when(meters.getIfAvailable(any(Supplier.class)))
                .thenReturn(new SimpleMeterRegistry());
        OpenAiCompatibleChatModelFactory factory =
                new OpenAiCompatibleChatModelFactory(observations, meters);

        OpenAiChatModel model = (OpenAiChatModel) factory.create(
                new SpringAiChatModelFactory.Request(
                        "https://provider.example/v1",
                        SecretValue.of("secret"),
                        "gpt-5.6-luna",
                        OpenAiReasoningEffort.NONE,
                        Duration.ofSeconds(30)));

        assertEquals("none", model.getOptions().getReasoningEffort());
    }
}
