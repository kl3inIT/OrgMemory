package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.tracing.otel.bridge.OtelSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The message under test is one an OrgMemory exception could realistically carry: a query and
 * an evidence excerpt concatenated into a diagnostic string. If any assertion here regresses,
 * that text reaches whatever collector the deployment points at.
 */
class ExceptionSanitizingSpanExporterTests {

    private static final String PAYLOAD =
            "no evidence for query 'quarterly revenue for ACME' in chunk 'ACME booked 4.2M in Q3'";

    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");
    private static final AttributeKey<String> EXCEPTION_MESSAGE = AttributeKey.stringKey("exception.message");
    private static final AttributeKey<String> EXCEPTION_STACKTRACE =
            AttributeKey.stringKey("exception.stacktrace");

    @Test
    void stripsTheMessageAndStackTraceMicrometerRecordsWhenASpanFails() {
        SpanData exported = export(span -> new OtelSpan(span).error(new IllegalStateException(PAYLOAD)));

        EventData exception = onlyEvent(exported);
        assertEquals("exception", exception.getName());
        assertNull(exception.getAttributes().get(EXCEPTION_MESSAGE), "the exception message is payload");
        assertNull(exception.getAttributes().get(EXCEPTION_STACKTRACE), "the stack trace is payload");
    }

    @Test
    void keepsTheExceptionTypeBecauseAClassNameCannotCarryCustomerText() {
        SpanData exported = export(span -> new OtelSpan(span).error(new IllegalStateException(PAYLOAD)));

        assertEquals(
                IllegalStateException.class.getName(),
                onlyEvent(exported).getAttributes().get(EXCEPTION_TYPE));
    }

    @Test
    void clearsTheStatusDescriptionMicrometerCopiesFromTheExceptionMessage() {
        SpanData exported = export(span -> new OtelSpan(span).error(new IllegalStateException(PAYLOAD)));

        assertEquals(StatusCode.ERROR, exported.getStatus().getStatusCode(), "the failure itself must survive");
        assertEquals("", exported.getStatus().getDescription());
    }

    @Test
    void leavesTheStrippedAttributesVisibleAsDroppedRatherThanNeverRecorded() {
        SpanData exported = export(span -> new OtelSpan(span).error(new IllegalStateException(PAYLOAD)));

        EventData exception = onlyEvent(exported);
        assertEquals(3, exception.getTotalAttributeCount(), "the original count is the honest one");
        assertEquals(2, exception.getDroppedAttributesCount());
    }

    @Test
    void leavesASuccessfulSpanExactlyAsItWas() {
        SpanData exported = export(span -> span.setAttribute("orgmemory.graph_rag.stage", "retrieve"));

        assertTrue(exported.getEvents().isEmpty());
        assertEquals("", exported.getStatus().getDescription());
        assertEquals(
                "retrieve",
                exported.getAttributes().get(AttributeKey.stringKey("orgmemory.graph_rag.stage")),
                "sanitization must not disturb the payload-free attributes the pipeline exists to carry");
    }

    @Test
    void refusesToLetAnUnmodelledEventAttributeThroughUninspected() {
        SpanData exported = export(span -> span.addEvent(
                "orgmemory.diagnostic",
                io.opentelemetry.api.common.Attributes.of(AttributeKey.stringKey("note"), PAYLOAD)));

        EventData event = onlyEvent(exported);
        assertEquals("orgmemory.diagnostic", event.getName(), "event names are code-derived and survive");
        assertNull(event.getAttributes().get(AttributeKey.stringKey("note")));
    }

    /** Runs one span through the real SDK and the exporter, and returns what the collector would see. */
    private static SpanData export(Consumer<io.opentelemetry.api.trace.Span> work) {
        InMemorySpanExporter collector = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(new ExceptionSanitizingSpanExporter(collector)))
                .build()) {
            Tracer tracer = provider.get("test");
            io.opentelemetry.api.trace.Span span = tracer.spanBuilder("test").startSpan();
            work.accept(span);
            span.end();
            return collector.getFinishedSpanItems().getFirst();
        }
    }

    private static EventData onlyEvent(SpanData span) {
        assertFalse(span.getEvents().isEmpty(), "the event itself must survive so the failure stays visible");
        assertEquals(1, span.getEvents().size());
        return span.getEvents().getFirst();
    }
}
