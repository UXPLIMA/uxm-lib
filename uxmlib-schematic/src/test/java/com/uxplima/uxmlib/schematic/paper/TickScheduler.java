package com.uxplima.uxmlib.schematic.paper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A scheduler with ticks a test turns by hand. A region or async task runs at once, on the test's thread; a
 * delayed region task waits for {@link #tick()}. The hops a paste and a capture do not take are refused, so
 * code that starts taking one fails here rather than running on the wrong thread.
 */
final class TickScheduler implements Scheduler {

    private static final TaskHandle DONE = new TaskHandle() {
        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    private List<Runnable> nextTick = new ArrayList<>();
    int regionHops;
    int asyncHops;

    /** Runs what waited for the next tick. Answers how many tasks ran. */
    int tick() {
        List<Runnable> due = nextTick;
        nextTick = new ArrayList<>();
        due.forEach(Runnable::run);
        return due.size();
    }

    /** Turns ticks until nothing waits. Answers how many ticks it took. */
    int runOut() {
        int ticks = 0;
        while (!nextTick.isEmpty()) {
            tick();
            ticks++;
        }
        return ticks;
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        regionHops++;
        task.run();
        return DONE;
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        nextTick.add(task);
        return DONE;
    }

    @Override
    public TaskHandle async(Runnable task) {
        asyncHops++;
        task.run();
        return DONE;
    }

    @Override
    public TaskHandle global(Runnable task) {
        throw new UnsupportedOperationException("a paste takes no global hop");
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a paste takes no global hop");
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a paste takes no global hop");
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a paste takes no repeating hop");
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        throw new UnsupportedOperationException("a paste takes no entity hop");
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a paste takes no entity hop");
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a paste takes no entity hop");
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a paste takes no delayed async hop");
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a paste takes no repeating hop");
    }
}
