package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assistant.AssistantUnavailableException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

class AssistantRetrievalSchedulerTests {

    @Test
    void rejectsWorkWithASanitizedFailureWhenTheBoundedQueueIsFull()
            throws InterruptedException {
        AssistantRetrievalScheduler scheduler =
                new AssistantRetrievalScheduler(1, 1, Duration.ofSeconds(1));
        CountDownLatch active = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Disposable first = scheduler.schedule(() -> {
                    active.countDown();
                    release.await();
                    return "active";
                })
                .subscribe();
        Disposable queued = null;
        try {
            assertTrue(active.await(2, TimeUnit.SECONDS));
            queued = scheduler.schedule(() -> "queued").subscribe();

            StepVerifier.create(scheduler.schedule(() -> "rejected"))
                    .expectErrorSatisfies(error ->
                            assertInstanceOf(AssistantUnavailableException.class, error))
                    .verify();
        } finally {
            release.countDown();
            first.dispose();
            if (queued != null) {
                queued.dispose();
            }
            scheduler.close();
        }
    }

    @Test
    void cancellationInterruptsActiveRetrieval() throws InterruptedException {
        AssistantRetrievalScheduler scheduler =
                new AssistantRetrievalScheduler(1, 1, Duration.ofSeconds(1));
        CountDownLatch active = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Disposable work = scheduler.schedule(() -> {
                    active.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException cancellation) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                        throw cancellation;
                    }
                    return "unreachable";
                })
                .subscribe();
        try {
            assertTrue(active.await(2, TimeUnit.SECONDS));
            work.dispose();
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        } finally {
            work.dispose();
            scheduler.close();
        }
    }
}
