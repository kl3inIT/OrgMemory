package com.orgmemory.worker.connector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the connector driver on a fixed delay. Disabled unless
 * {@code orgmemory.connector.scheduling-enabled=true}, so the connector stays inert in
 * deployments that have not opted in.
 */
@Component
@ConditionalOnProperty(
        prefix = "orgmemory.connector",
        name = "scheduling-enabled",
        havingValue = "true")
class ConnectorCrawlScheduler {

    private final ConnectorCrawlRunner runner;

    ConnectorCrawlScheduler(ConnectorCrawlRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${orgmemory.connector.poll-interval:30s}")
    void poll() {
        runner.runPending();
    }
}
