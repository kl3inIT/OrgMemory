package com.orgmemory.worker.ingestion;

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
        prefix = "orgmemory.ingestion.processing",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
class SourceIngestionScheduler implements ApplicationListener<ContextClosedEvent> {

    private final SourceIngestionProcessor processor;
    private final SourceProcessingProperties properties;
    private final LongSupplier nanoTime;
    private volatile boolean acceptingWork = true;

    @Autowired
    SourceIngestionScheduler(
            SourceIngestionProcessor processor,
            SourceProcessingProperties properties) {
        this(processor, properties, System::nanoTime);
    }

    SourceIngestionScheduler(
            SourceIngestionProcessor processor,
            SourceProcessingProperties properties,
            LongSupplier nanoTime) {
        this.processor = Objects.requireNonNull(processor, "processor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Scheduled(fixedDelayString = "${orgmemory.ingestion.processing.poll-interval:2s}")
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
