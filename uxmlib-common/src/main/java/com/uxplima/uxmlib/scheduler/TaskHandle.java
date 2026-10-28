package com.uxplima.uxmlib.scheduler;

/**
 * A handle to a scheduled task. Returned by every {@link Scheduler} method so the caller can cancel a
 * delayed or repeating task and check whether it is still live.
 *
 * <p>The handle a repeating task is handed on each run equals the handle its {@code ...Timer} call returned, so
 * a caller that keeps the returned handle in a map can remove it with {@code map.remove(key, handle)} from inside
 * the task.
 */
public interface TaskHandle {

    /** Cancel the task. Safe to call more than once; a no-op once the task has finished or been cancelled. */
    void cancel();

    /** Whether the task has been cancelled (or never started, e.g. its entity was already removed). */
    boolean isCancelled();
}
