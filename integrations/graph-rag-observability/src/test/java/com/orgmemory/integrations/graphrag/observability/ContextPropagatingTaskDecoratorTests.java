package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * A task submitted to a virtual thread starts with no observation context, so a span opened
 * inside it becomes a root of its own and the job that caused it is unrecoverable. These prove
 * the decorator restores the submitting thread's context, and — the part that is easy to get
 * wrong — that it captures at submission rather than at execution.
 */
class ContextPropagatingTaskDecoratorTests {

    private static final ThreadLocal<String> AMBIENT = new ThreadLocal<>();

    private final ContextSnapshotFactory snapshots = ContextSnapshotFactory.builder()
            .contextRegistry(registryFor(AMBIENT))
            .build();

    @Test
    void aTaskOnAFreshThreadSeesTheContextOfTheThreadThatSubmittedIt() throws Exception {
        var decorator = new ContextPropagatingTaskDecorator(snapshots);
        AMBIENT.set("job-42");

        Callable<String> decorated = decorator.decorate(AMBIENT::get);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertEquals(
                    "job-42",
                    executor.submit(decorated).get(),
                    "without this the span opened here has no parent and cannot be attributed");
        } finally {
            AMBIENT.remove();
        }
    }

    @Test
    void anUndecoratedTaskProvesTheFixtureWouldHaveCaughtANoOp() throws Exception {
        AMBIENT.set("job-42");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertNull(
                    executor.submit(AMBIENT::get).get(),
                    "a fresh thread must start empty, or the test above proves nothing");
        } finally {
            AMBIENT.remove();
        }
    }

    /**
     * Capturing when the task runs would read the executing thread's empty context and restore
     * nothing — a mistake that looks identical to success from everywhere except the trace.
     */
    @Test
    void capturesWhenTheTaskIsDecoratedRatherThanWhenItRuns() throws Exception {
        var decorator = new ContextPropagatingTaskDecorator(snapshots);
        AMBIENT.set("at-decoration");
        Callable<String> decorated = decorator.decorate(AMBIENT::get);
        AMBIENT.set("after-decoration");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertEquals("at-decoration", executor.submit(decorated).get());
        } finally {
            AMBIENT.remove();
        }
    }

    @Test
    void restoresTheSubmittingThreadsContextWithoutLeavingItSetAfterwards() throws Exception {
        var decorator = new ContextPropagatingTaskDecorator(snapshots);
        AMBIENT.set("job-42");
        Runnable decorated = decorator.decorate(() -> assertNotNull(AMBIENT.get()));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(decorated).get();
            assertNull(
                    executor.submit(AMBIENT::get).get(),
                    "the scope must close, or one job's context leaks into the next task");
        } finally {
            AMBIENT.remove();
        }
    }

    private static ContextRegistry registryFor(ThreadLocal<String> holder) {
        ContextRegistry registry = new ContextRegistry();
        registry.registerThreadLocalAccessor(
                "test.ambient",
                holder::get,
                holder::set,
                holder::remove);
        return registry;
    }
}
