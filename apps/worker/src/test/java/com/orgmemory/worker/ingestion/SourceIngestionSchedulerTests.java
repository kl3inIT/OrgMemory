package com.orgmemory.worker.ingestion;

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

class SourceIngestionSchedulerTests {

    @Test
    void stopsTheBurstWhenNoWorkIsAvailable() {
        SourceIngestionProcessor processor = mock(SourceIngestionProcessor.class);
        when(processor.processNext()).thenReturn(WorkProcessingResult.EMPTY_OR_DEFERRED);
        SourceIngestionScheduler scheduler =
                new SourceIngestionScheduler(processor, properties(10, Duration.ofSeconds(30)));

        scheduler.poll();

        verify(processor).processNext();
    }

    @Test
    void capsEachBurstSoOtherScheduledQueuesCanRun() {
        SourceIngestionProcessor processor = mock(SourceIngestionProcessor.class);
        when(processor.processNext()).thenReturn(WorkProcessingResult.PROCESSED);
        SourceIngestionScheduler scheduler =
                new SourceIngestionScheduler(processor, properties(3, Duration.ofSeconds(30)));

        scheduler.poll();

        verify(processor, times(3)).processNext();
    }

    @Test
    void stopsBeforeTheNextClaimWhenInterrupted() {
        SourceIngestionProcessor processor = mock(SourceIngestionProcessor.class);
        when(processor.processNext()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return WorkProcessingResult.PROCESSED;
        });
        SourceIngestionScheduler scheduler =
                new SourceIngestionScheduler(processor, properties(10, Duration.ofSeconds(30)));

        try {
            scheduler.poll();
            verify(processor).processNext();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void stopsBeforeTheNextClaimWhenTheWallClockBudgetExpires() {
        SourceIngestionProcessor processor = mock(SourceIngestionProcessor.class);
        when(processor.processNext()).thenReturn(WorkProcessingResult.PROCESSED);
        AtomicLong nanoTime = new AtomicLong();
        SourceIngestionScheduler scheduler = new SourceIngestionScheduler(
                processor,
                properties(10, Duration.ofNanos(10)),
                () -> nanoTime.getAndAdd(6));

        scheduler.poll();

        verify(processor).processNext();
    }

    @Test
    void refusesNewClaimsAfterContextShutdownStarts() {
        SourceIngestionProcessor processor = mock(SourceIngestionProcessor.class);
        SourceIngestionScheduler scheduler =
                new SourceIngestionScheduler(processor, properties(10, Duration.ofSeconds(30)));

        scheduler.onApplicationEvent(mock(ContextClosedEvent.class));
        scheduler.poll();

        verify(processor, never()).processNext();
    }

    @Test
    void bindsTheSourceBurstLimits() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "orgmemory.ingestion.processing.max-jobs-per-invocation=7",
                        "orgmemory.ingestion.processing.max-wall-clock=45s")
                .run(context -> {
                    SourceProcessingProperties properties =
                            context.getBean(SourceProcessingProperties.class);
                    assertEquals(7, properties.maxJobsPerInvocation());
                    assertEquals(Duration.ofSeconds(45), properties.maxWallClock());
                });
    }

    private static SourceProcessingProperties properties(
            int maxJobsPerInvocation,
            Duration maxWallClock) {
        return new SourceProcessingProperties(
                "source-worker-test",
                Duration.ofMinutes(5),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                maxJobsPerInvocation,
                maxWallClock);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SourceProcessingProperties.class)
    static class PropertiesConfiguration {}
}
