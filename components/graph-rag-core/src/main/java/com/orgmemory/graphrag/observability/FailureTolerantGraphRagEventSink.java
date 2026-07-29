package com.orgmemory.graphrag.observability;

import java.lang.System.Logger;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Absorbs a failing telemetry backend without letting the failure disappear.
 *
 * <p>Producers already treat emission as non-critical and catch whatever the sink
 * throws, which is correct — an observability backend must never decide whether a
 * retrieval or an indexing job succeeds. The cost is that a sink broken since
 * startup looks exactly like a sink with nothing to report. This wrapper keeps the
 * absorption and adds the signal that was missing: a running count, the type of the
 * most recent failure, and one log line each time the failure changes kind.
 *
 * <p>Only class names are recorded. A telemetry backend's exception message can
 * quote the request it failed to send, so the message and the stack trace are
 * treated the same way as any other payload and never leave this class.
 *
 * <p>Logging goes through {@link System.Logger} so that
 * {@code components/graph-rag-core} keeps its property of having no runtime
 * dependencies; Spring Boot's JUL bridge routes it into the application log.
 */
public final class FailureTolerantGraphRagEventSink implements GraphRagEventSink {

    private static final Logger LOGGER =
            System.getLogger(FailureTolerantGraphRagEventSink.class.getName());

    private final GraphRagEventSink delegate;
    private final AtomicLong swallowedFailures = new AtomicLong();
    private final AtomicReference<String> lastFailureType = new AtomicReference<>();

    FailureTolerantGraphRagEventSink(GraphRagEventSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void emit(GraphRagEvent event) {
        try {
            delegate.emit(event);
        } catch (RuntimeException failure) {
            record(failure);
        }
    }

    /** How many events this sink has dropped since startup. Never resets. */
    public long swallowedFailureCount() {
        return swallowedFailures.get();
    }

    /** Class name of the most recent failure, or {@code null} while nothing has failed. */
    public String lastFailureType() {
        return lastFailureType.get();
    }

    private void record(RuntimeException failure) {
        swallowedFailures.incrementAndGet();
        String failureType = failure.getClass().getName();
        // One line per change of kind: a permanently broken sink must not flood the
        // log at event rate, but a sink that starts failing differently must say so.
        if (!failureType.equals(lastFailureType.getAndSet(failureType))) {
            LOGGER.log(
                    Logger.Level.WARNING,
                    "GraphRAG telemetry sink {0} is failing with {1}; events are being dropped."
                            + " Message and stack trace are withheld because a telemetry failure"
                            + " can quote the event it could not send.",
                    delegate.getClass().getName(),
                    failureType);
        }
    }

    @Override
    public String toString() {
        return "FailureTolerantGraphRagEventSink{" + delegate + "}";
    }
}
