package com.orgmemory.worker.graph;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.indexing",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
class GraphIndexingScheduler {

    private final GraphIndexingProcessor processor;

    GraphIndexingScheduler(GraphIndexingProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${orgmemory.graph-rag.indexing.poll-interval:3s}")
    void poll() {
        processor.processNext();
    }
}
