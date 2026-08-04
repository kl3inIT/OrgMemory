package com.orgmemory.api.assistant;

import com.orgmemory.core.assistant.AssistantUnavailableException;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Bounded, cancellable scheduler for blocking permission-scoped retrieval. */
final class AssistantRetrievalScheduler implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final Scheduler scheduler;
    private final ContextSnapshotFactory snapshots;
    private final Duration shutdownTimeout;

    AssistantRetrievalScheduler(
            int maximumConcurrency,
            int queueCapacity,
            Duration shutdownTimeout) {
        if (maximumConcurrency < 1) {
            throw new IllegalArgumentException("maximumConcurrency must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(
                    task,
                    "assistant-retrieval-" + threadNumber.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                maximumConcurrency,
                maximumConcurrency,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threads,
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        scheduler = Schedulers.fromExecutorService(executor, "assistant-retrieval");
        snapshots = ContextSnapshotFactory.builder().build();
    }

    <T> Mono<T> schedule(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return Mono.defer(() -> {
                    ContextSnapshot snapshot = snapshots.captureAll();
                    return Mono.fromCallable(() -> {
                        try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                            return task.call();
                        }
                    }).subscribeOn(scheduler);
                })
                .onErrorMap(
                        error -> error instanceof RejectedExecutionException
                                || error.getCause() instanceof RejectedExecutionException,
                        error -> new AssistantUnavailableException(
                                "The assistant is temporarily busy", error));
    }

    @Override
    public void close() {
        scheduler.dispose();
        executor.shutdownNow();
        try {
            executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
