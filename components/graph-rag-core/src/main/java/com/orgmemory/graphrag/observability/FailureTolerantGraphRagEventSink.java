package com.orgmemory.graphrag.observability;

import java.lang.System.Logger;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * most recent failure, and one log line the first time each kind of failure appears.
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

    /**
     * How many distinct failure types are worth a log line. A backend that produces more
     * than this is malfunctioning in a way one more line will not clarify.
     */
    private static final int REPORTED_FAILURE_TYPE_LIMIT = 10;

    private final GraphRagEventSink delegate;
    private final AtomicLong swallowedFailures = new AtomicLong();
    private final AtomicReference<String> lastFailureType = new AtomicReference<>();
    private final Set<String> reportedFailureTypes = ConcurrentHashMap.newKeySet();

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

    /** How many log lines this sink has produced. One per distinct failure kind. */
    int reportedFailureTypeCount() {
        return reportedFailureTypes.size();
    }

    private void record(RuntimeException failure) {
        swallowedFailures.incrementAndGet();
        String failureType = failure.getClass().getName();
        lastFailureType.set(failureType);
        // One line the first time each kind appears. Comparing against the previous kind
        // alone would flood at event rate as soon as a broken backend alternates, which a
        // timeout that retries as a connection failure does immediately.
        if (reportedFailureTypes.size() < REPORTED_FAILURE_TYPE_LIMIT
                && reportedFailureTypes.add(failureType)) {
            LOGGER.log(
                    Logger.Level.WARNING,
                    "GraphRAG telemetry sink {0} is failing with {1}; events are being dropped."
                            + " Message and stack trace are withheld because a telemetry failure"
                            + " can quote the event it could not send. Later failures of this kind"
                            + " are counted rather than logged.",
                    delegate.getClass().getName(),
                    failureType);
        }
    }

    @Override
    public String toString() {
        return "FailureTolerantGraphRagEventSink{" + delegate + "}";
    }
}
