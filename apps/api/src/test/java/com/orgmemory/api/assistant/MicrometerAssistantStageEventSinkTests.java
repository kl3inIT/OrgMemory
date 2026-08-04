package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MicrometerAssistantStageEventSinkTests {

    @Test
    void recordsOnlyBoundedStageDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var sink = new MicrometerAssistantStageEventSink(registry);

        sink.emit(new AssistantStageEventSink.AssistantStageEvent(
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG,
                AssistantStageEventSink.Stage.GROUNDING_TO_PROMPT,
                AssistantStageEventSink.Outcome.SUCCEEDED,
                Duration.ofMillis(25),
                null,
                Instant.parse("2026-08-05T01:02:03Z")));

        Timer timer = registry.get(
                        MicrometerAssistantStageEventSink.STAGE_TIMER)
                .timer();
        assertEquals(25.0, timer.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(
                Set.of("engine", "stage", "outcome"),
                timer.getId().getTags().stream()
                        .map(tag -> tag.getKey())
                        .collect(Collectors.toSet()));
    }
}
