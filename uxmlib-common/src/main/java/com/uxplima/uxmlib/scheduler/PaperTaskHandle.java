package com.uxplima.uxmlib.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.jspecify.annotations.Nullable;

/**
 * A {@link TaskHandle} backing onto a Paper {@link ScheduledTask}. A {@code null} task means the work
 * never started (the entity scheduler returns {@code null} when the entity has already been removed), so
 * the handle reports itself cancelled and {@link #cancel()} is a no-op.
 */
final class PaperTaskHandle implements TaskHandle {

    private final @Nullable ScheduledTask task;

    PaperTaskHandle(@Nullable ScheduledTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }

    /**
     * Two handles on the same Paper task are equal. A repeating task is handed a new handle on every run, so a
     * caller that keeps the handle the scheduler returned can still find or remove it with the one its task was
     * handed. A handle on work that never started equals only itself.
     */
    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }
        return task != null && other instanceof PaperTaskHandle handle && handle.task == task;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(task == null ? this : task);
    }
}
