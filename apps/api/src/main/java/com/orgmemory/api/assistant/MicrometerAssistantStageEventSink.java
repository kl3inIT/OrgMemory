package com.orgmemory.api.assistant;

import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.Objects;

/** Bounded-cardinality timer for assistant latency attribution stages. */
final class MicrometerAssistantStageEventSink
        implements AssistantStageEventSink {

    static final String STAGE_TIMER = "orgmemory.assistant.stage";

    private final MeterRegistry registry;

    MicrometerAssistantStageEventSink(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void emit(AssistantStageEvent event) {
        Objects.requireNonNull(event, "event");
        Timer.builder(STAGE_TIMER)
                .description(
                        "Assistant latency stages above permission-aware retrieval")
                .tag("engine", value(event.engine()))
                .tag("stage", value(event.stage()))
                .tag("outcome", value(event.outcome()))
                .register(registry)
                .record(event.duration());
    }

    private static String value(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
