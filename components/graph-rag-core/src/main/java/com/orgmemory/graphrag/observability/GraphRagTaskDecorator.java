package com.orgmemory.graphrag.observability;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Carries ambient observation context across a thread boundary.
 *
 * <p>GraphRAG fans work out to virtual threads in two places — chunk extraction
 * in the worker, and per-snapshot retrieval in the application. A task started
 * on a fresh thread begins with no observation context, so every span a model
 * call opens inside one of those tasks becomes a root of its own. The job or
 * request that caused it is then unrecoverable: the spans exist, they are simply
 * no longer attached to anything that explains them.
 *
 * <p>This is a port rather than a direct dependency because the modules that own
 * those executors are domain modules with no telemetry library on their
 * classpath, and putting one there to solve a tracing problem would decide a
 * module boundary in passing. The implementation lives beside the other
 * telemetry adapters; absent one, {@link #NONE} keeps the executors behaving
 * exactly as they did.
 *
 * <p>Nothing about the concurrency itself changes: the same executors are
 * created with the same lifetimes and the same bounds, and the decorator only
 * wraps what is submitted to them.
 */
@FunctionalInterface
public interface GraphRagTaskDecorator {

    /** Leaves tasks untouched, for a deployment with no tracing configured. */
    GraphRagTaskDecorator NONE = new GraphRagTaskDecorator() {

        @Override
        public <T> Callable<T> decorate(Callable<T> task) {
            return task;
        }

        @Override
        public String toString() {
            return "GraphRagTaskDecorator.NONE";
        }
    };

    <T> Callable<T> decorate(Callable<T> task);

    default Runnable decorate(Runnable task) {
        Callable<Void> decorated = decorate(() -> {
            task.run();
            return null;
        });
        return () -> {
            try {
                decorated.call();
            } catch (RuntimeException | Error propagated) {
                throw propagated;
            } catch (Exception checked) {
                // Runnable cannot declare one, and a decorator must not change
                // what the task it wraps is allowed to throw.
                throw new IllegalStateException(checked);
            }
        };
    }

    default <T> Supplier<T> decorateSupplier(Supplier<T> task) {
        Callable<T> decorated = decorate(task::get);
        return () -> {
            try {
                return decorated.call();
            } catch (RuntimeException | Error propagated) {
                throw propagated;
            } catch (Exception checked) {
                throw new IllegalStateException(checked);
            }
        };
    }
}
