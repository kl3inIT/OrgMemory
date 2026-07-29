package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Traces are sampled, so the p95 of a stage is not answerable from them. These meters are the
 * unsampled half of the same events. The tag set is the thing worth guarding: every dimension
 * multiplies stored series, so an identifier that grows with tenants or requests would turn a
 * useful metric into an unbounded one.
 */
class MicrometerGraphRagEventSinkTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GraphRagEventSink sink = new MicrometerGraphRagEventSink(registry);

    @Test
    void recordsTheOriginalStageDurationRatherThanTheTimeItWasObserved() {
        sink.emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, Duration.ofMillis(125)));

        assertEquals(
                125.0,
                registry.get(MicrometerGraphRagEventSink.STAGE_TIMER).timer().totalTime(TimeUnit.MILLISECONDS),
                0.001);
    }

    @Test
    void separatesStagesOutcomesAndCacheStatusSoLatencyCanBeAttributed() {
        sink.emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, Duration.ofMillis(10)));

        assertEquals(
                Set.of("stage", "outcome", "cache_status"),
                tagKeysOf(MicrometerGraphRagEventSink.STAGE_TIMER),
                "these are the questions the timer exists to answer");
    }

    @Test
    void carriesNoIdentifierThatWouldGrowASeriesPerTenantOrRequest() {
        sink.emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, Duration.ofMillis(10)));

        Set<String> forbidden = Set.of(
                "organization_id", "operation_id", "scope_fingerprint", "model_route_fingerprint");
        for (Meter meter : registry.getMeters()) {
            Set<String> tags = meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
            assertTrue(
                    forbidden.stream().noneMatch(tags::contains),
                    () -> meter.getId().getName() + " carries an unbounded tag: " + tags
                            + ". Those belong on the span, where each is one record rather than "
                            + "a permanent series.");
        }
    }

    @Test
    void countsFailuresByCodeSoABrokenStageIsAttributableWithoutReadingTraces() {
        sink.emit(event(GraphRagEventSink.Outcome.FAILED, "retrieval_unavailable", Duration.ofMillis(3)));

        assertEquals(
                1.0,
                registry.get(MicrometerGraphRagEventSink.FAILURE_COUNTER)
                        .tag("failure_code", "retrieval_unavailable")
                        .counter()
                        .count());
    }

    @Test
    void keepsTheFailureCodeOffTheTimerBecauseOnlyItsShapeIsBounded() {
        sink.emit(event(GraphRagEventSink.Outcome.FAILED, "retrieval_unavailable", Duration.ofMillis(3)));

        assertNull(
                registry.find(MicrometerGraphRagEventSink.STAGE_TIMER).timer().getId().getTag("failure_code"),
                "the port bounds failureCode's shape, not its set of values");
    }

    @Test
    void accumulatesTheWorkAStageConsumedAndProduced() {
        sink.emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, Duration.ofMillis(1)));
        sink.emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, Duration.ofMillis(1)));

        assertEquals(8.0, registry.get(MicrometerGraphRagEventSink.INPUT_COUNTER).counter().count());
        assertEquals(4.0, registry.get(MicrometerGraphRagEventSink.OUTPUT_COUNTER).counter().count());
    }

    @Test
    void reportsAnAbsentCacheStatusAsItsOwnValueRatherThanDroppingTheTag() {
        sink.emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, Duration.ofMillis(1)));

        assertEquals(
                "none",
                registry.get(MicrometerGraphRagEventSink.STAGE_TIMER).timer().getId().getTag("cache_status"),
                "a missing tag would silently merge cached and uncached stages into one series");
    }

    private Set<String> tagKeysOf(String meterName) {
        List<Tag> tags = registry.get(meterName).timer().getId().getTags();
        return tags.stream().map(Tag::getKey).collect(Collectors.toSet());
    }

    private static GraphRagEventSink.GraphRagEvent event(
            GraphRagEventSink.Outcome outcome, String failureCode, Duration duration) {
        return new GraphRagEventSink.GraphRagEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GraphRagEventSink.Stage.RETRIEVE,
                outcome,
                duration,
                4,
                2,
                null,
                null,
                null,
                failureCode,
                Instant.now());
    }
}
