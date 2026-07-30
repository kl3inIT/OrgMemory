package com.orgmemory.integrations.graphrag.observability;

import com.orgmemory.graphrag.observability.GraphRagTaskDecorator;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Captures the submitting thread's observation context and restores it around
 * the task, so a span opened inside a virtual thread keeps the job or request
 * that submitted it as its parent.
 *
 * <p>The snapshot is taken when the task is decorated — on the submitting
 * thread, while the context still exists — and not when it runs. Capturing at
 * run time would read the fresh thread's empty context and restore nothing,
 * which is the failure this class exists to prevent and one that looks like
 * success from every angle except the trace.
 */
public final class ContextPropagatingTaskDecorator implements GraphRagTaskDecorator {

    private final ContextSnapshotFactory snapshots;

    public ContextPropagatingTaskDecorator(ContextSnapshotFactory snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public <T> Callable<T> decorate(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        ContextSnapshot snapshot = snapshots.captureAll();
        return () -> {
            try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                return task.call();
            }
        };
    }

    @Override
    public String toString() {
        return "ContextPropagatingTaskDecorator";
    }
}
