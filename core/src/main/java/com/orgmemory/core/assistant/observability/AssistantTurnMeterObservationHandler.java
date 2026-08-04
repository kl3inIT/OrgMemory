package com.orgmemory.core.assistant.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.Locale;
import java.util.Objects;

/**
 * Records time to first token as its own distribution.
 *
 * <p>The observation's own timer already measures the whole turn, and Spring AI's
 * {@code gen_ai.client.operation} measures the model call, but neither answers the question a
 * user's complaint actually raises: how long the screen stayed blank. That interval is not a
 * fraction of either — it contains permission-scoped retrieval and excludes the entire
 * generation that follows the first token — so it needs a distribution of its own rather than
 * a percentage of something else.
 *
 * <p>Only {@code engine} tags it. Outcome is left off deliberately: a turn that never emitted
 * a token records nothing here at all, so the only outcomes this timer could ever carry are
 * the ones that started, and a tag with one reachable value is a tag that costs storage to
 * say nothing.
 *
 * <p>The handler follows Spring AI's {@code ChatModelMeterObservationHandler}: meters are
 * recorded by a handler on the registry rather than by the instrumented code, so the assistant
 * knows about {@code ObservationRegistry} and nothing about how anyone stores the result.
 */
public class AssistantTurnMeterObservationHandler
        implements ObservationHandler<AssistantTurnObservationContext> {

    public static final String TIME_TO_FIRST_TOKEN = "orgmemory.assistant.time_to_first_token";
    public static final String RETRIEVAL_DURATION = "orgmemory.assistant.retrieval.duration";

    private final MeterRegistry registry;

    public AssistantTurnMeterObservationHandler(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void onStop(AssistantTurnObservationContext context) {
        AssistantTurnEvent event = context.toEvent();
        Timer.builder(RETRIEVAL_DURATION)
                .description("Time from an assistant question to permission-scoped retrieval completion")
                .tag(
                        AssistantTurnObservationDocumentation.LowCardinality.ENGINE.asString(),
                        event.engine().name().toLowerCase(Locale.ROOT))
                .register(registry)
                .record(event.retrieval());
        if (!event.started()) {
            return;
        }
        Timer.builder(TIME_TO_FIRST_TOKEN)
                .description("Time from an assistant question to the first token the caller sees")
                .tag(
                        AssistantTurnObservationDocumentation.LowCardinality.ENGINE.asString(),
                        event.engine().name().toLowerCase(Locale.ROOT))
                .register(registry)
                .record(event.timeToFirstToken());
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof AssistantTurnObservationContext;
    }
}
