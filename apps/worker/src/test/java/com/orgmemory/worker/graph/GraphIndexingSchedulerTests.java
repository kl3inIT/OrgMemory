package com.orgmemory.worker.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.worker.WorkProcessingResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

class GraphIndexingSchedulerTests {

    @Test
    void stopsTheBurstWhenNoWorkIsAvailable() {
        GraphIndexingProcessor processor = mock(GraphIndexingProcessor.class);
        when(processor.processNext()).thenReturn(WorkProcessingResult.EMPTY_OR_DEFERRED);
        GraphIndexingScheduler scheduler =
                new GraphIndexingScheduler(processor, properties(5, Duration.ofSeconds(30)));

        scheduler.poll();

        verify(processor).processNext();
    }

    @Test
    void capsEachBurstSoSourceAndMaintenanceSchedulersCanRun() {
        GraphIndexingProcessor processor = mock(GraphIndexingProcessor.class);
        when(processor.processNext()).thenReturn(WorkProcessingResult.PROCESSED);
        GraphIndexingScheduler scheduler =
                new GraphIndexingScheduler(processor, properties(2, Duration.ofSeconds(30)));

        scheduler.poll();

        verify(processor, times(2)).processNext();
    }

    @Test
    void stopsBeforeTheNextClaimWhenInterrupted() {
        GraphIndexingProcessor processor = mock(GraphIndexingProcessor.class);
        when(processor.processNext()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return WorkProcessingResult.PROCESSED;
        });
        GraphIndexingScheduler scheduler =
                new GraphIndexingScheduler(processor, properties(5, Duration.ofSeconds(30)));

        try {
            scheduler.poll();
            verify(processor).processNext();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void stopsBeforeTheNextClaimWhenTheWallClockBudgetExpires() {
        GraphIndexingProcessor processor = mock(GraphIndexingProcessor.class);
        when(processor.processNext()).thenReturn(WorkProcessingResult.PROCESSED);
        AtomicLong nanoTime = new AtomicLong();
        GraphIndexingScheduler scheduler = new GraphIndexingScheduler(
                processor,
                properties(5, Duration.ofNanos(10)),
                () -> nanoTime.getAndAdd(6));

        scheduler.poll();

        verify(processor).processNext();
    }

    @Test
    void refusesNewClaimsAfterContextShutdownStarts() {
        GraphIndexingProcessor processor = mock(GraphIndexingProcessor.class);
        GraphIndexingScheduler scheduler =
                new GraphIndexingScheduler(processor, properties(5, Duration.ofSeconds(30)));

        scheduler.onApplicationEvent(mock(ContextClosedEvent.class));
        scheduler.poll();

        verify(processor, never()).processNext();
    }

    @Test
    void bindsTheGraphBurstLimits() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "orgmemory.graph-rag.indexing.max-jobs-per-invocation=4",
                        "orgmemory.graph-rag.indexing.max-wall-clock=50s")
                .run(context -> {
                    GraphIndexingProperties properties =
                            context.getBean(GraphIndexingProperties.class);
                    assertEquals(4, properties.maxJobsPerInvocation());
                    assertEquals(Duration.ofSeconds(50), properties.maxWallClock());
                });
    }

    private static GraphIndexingProperties properties(
            int maxJobsPerInvocation,
            Duration maxWallClock) {
        return new GraphIndexingProperties(
                "graph-worker-test",
                Duration.ofMinutes(10),
                Duration.ofMinutes(2),
                4,
                maxJobsPerInvocation,
                maxWallClock);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GraphIndexingProperties.class)
    static class PropertiesConfiguration {}
}
