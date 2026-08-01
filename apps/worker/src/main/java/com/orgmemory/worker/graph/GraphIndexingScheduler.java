package com.orgmemory.worker.graph;

import com.orgmemory.worker.WorkProcessingResult;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.indexing",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
class GraphIndexingScheduler implements ApplicationListener<ContextClosedEvent> {

    private final GraphIndexingProcessor processor;
    private final GraphIndexingProperties properties;
    private final LongSupplier nanoTime;
    private volatile boolean acceptingWork = true;

    @Autowired
    GraphIndexingScheduler(
            GraphIndexingProcessor processor,
            GraphIndexingProperties properties) {
        this(processor, properties, System::nanoTime);
    }

    GraphIndexingScheduler(
            GraphIndexingProcessor processor,
            GraphIndexingProperties properties,
            LongSupplier nanoTime) {
        this.processor = Objects.requireNonNull(processor, "processor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Scheduled(fixedDelayString = "${orgmemory.graph-rag.indexing.poll-interval:3s}")
    void poll() {
        long startedAt = nanoTime.getAsLong();
        for (int processed = 0;
                processed < properties.maxJobsPerInvocation();
                processed++) {
            if (mustStop(startedAt)) {
                return;
            }
            if (processor.processNext() != WorkProcessingResult.PROCESSED) {
                return;
            }
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        acceptingWork = false;
    }

    private boolean mustStop(long startedAt) {
        return !acceptingWork
                || Thread.currentThread().isInterrupted()
                || nanoTime.getAsLong() - startedAt
                        >= properties.maxWallClock().toNanos();
    }
}
