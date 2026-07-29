package com.orgmemory.integrations.graphrag.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Removes exception text from every span on its way out of the process.
 *
 * <p>Micrometer's OpenTelemetry bridge ends an errored span by calling
 * {@code recordException(throwable)} and {@code setStatus(ERROR, throwable.getMessage())}.
 * Both carry an arbitrary message and a stack trace, and an OrgMemory exception can be
 * raised while holding a query, an evidence chunk, a document identifier or a provider
 * response body. Nothing upstream can prove otherwise, so the message and the stack trace
 * are treated as payload and dropped.
 *
 * <p>The exception's type survives: it is a class name fixed by the source, it is the part
 * an operator actually needs, and it cannot carry customer text. Attribute counts are left
 * at their original values so a stripped attribute still shows up as dropped rather than as
 * never recorded.
 *
 * <p>This exporter deliberately does not filter span attributes. Their allowlist is a wider
 * question than exception handling, and pretending to cover it here would make a guarantee
 * this class does not enforce.
 *
 * <p>Micrometer's {@code SpanFilter} is the natural hook for the events but cannot reach the
 * status description, which {@code DelegatingSpanData} does not make mutable. Wrapping the
 * exporter covers both, and it runs after every filter, so it is the last gate before egress.
 */
public final class ExceptionSanitizingSpanExporter implements SpanExporter {

    /** Event attributes that survive sanitization; everything else is treated as payload. */
    static final Set<String> RETAINED_EVENT_ATTRIBUTES = Set.of("exception.type");

    private final SpanExporter delegate;

    public ExceptionSanitizingSpanExporter(SpanExporter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> sanitized = new ArrayList<>(spans.size());
        for (SpanData span : spans) {
            sanitized.add(new SanitizedSpanData(span));
        }
        return delegate.export(sanitized);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public String toString() {
        return "ExceptionSanitizingSpanExporter{" + delegate + "}";
    }

    private static final class SanitizedSpanData extends DelegatingSpanData {

        private final List<EventData> events;
        private final StatusData status;

        private SanitizedSpanData(SpanData delegate) {
            super(delegate);
            this.events = sanitizeEvents(delegate.getEvents());
            this.status = sanitizeStatus(delegate.getStatus());
        }

        @Override
        public List<EventData> getEvents() {
            return events;
        }

        @Override
        public StatusData getStatus() {
            return status;
        }

        private static List<EventData> sanitizeEvents(List<EventData> events) {
            List<EventData> sanitized = new ArrayList<>(events.size());
            for (EventData event : events) {
                sanitized.add(sanitizeEvent(event));
            }
            return List.copyOf(sanitized);
        }

        private static EventData sanitizeEvent(EventData event) {
            Attributes retained = retainAllowedAttributes(event.getAttributes());
            if (retained.size() == event.getAttributes().size()) {
                return event;
            }
            return EventData.create(
                    event.getEpochNanos(),
                    event.getName(),
                    retained,
                    event.getTotalAttributeCount());
        }

        private static Attributes retainAllowedAttributes(Attributes attributes) {
            AttributesBuilder builder = Attributes.builder();
            attributes.forEach((key, value) -> {
                if (RETAINED_EVENT_ATTRIBUTES.contains(key.getKey())) {
                    @SuppressWarnings("unchecked")
                    AttributeKey<Object> typedKey = (AttributeKey<Object>) key;
                    builder.put(typedKey, value);
                }
            });
            return builder.build();
        }

        private static StatusData sanitizeStatus(StatusData status) {
            if (status.getDescription().isEmpty()) {
                return status;
            }
            return StatusData.create(status.getStatusCode(), "");
        }
    }
}
