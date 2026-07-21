package com.orgmemory.worker.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "orgmemory.ingestion.processing",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
class SourceIngestionScheduler {

    private final SourceIngestionProcessor processor;

    SourceIngestionScheduler(SourceIngestionProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${orgmemory.ingestion.processing.poll-interval:2s}")
    void poll() {
        processor.processNext();
    }
}
