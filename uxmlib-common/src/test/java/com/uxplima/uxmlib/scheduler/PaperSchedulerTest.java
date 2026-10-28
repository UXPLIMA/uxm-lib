package com.uxplima.uxmlib.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaperSchedulerTest {

    private final List<Consumer<ScheduledTask>> runs = new ArrayList<>();
    private final ScheduledTask scheduled = proxy(ScheduledTask.class, (method, args) -> null);
    private final EntityScheduler entityScheduler = proxy(EntityScheduler.class, (method, args) -> {
        if (!method.equals("runAtFixedRate")) {
            throw new UnsupportedOperationException(method);
        }
        @SuppressWarnings("unchecked")
        Consumer<ScheduledTask> run = (Consumer<ScheduledTask>) args[1];
        runs.add(run);
        return scheduled;
    });
    private final Entity entity = proxy(Entity.class, (method, args) -> switch (method) {
        case "getScheduler" -> entityScheduler;
        default -> null;
    });
    private final Plugin plugin = proxy(Plugin.class, (method, args) -> switch (method) {
        case "isEnabled" -> true;
        default -> null;
    });
    private final PaperScheduler scheduler = new PaperScheduler(plugin);

    @Test
    void aRepeatingTaskCanRemoveTheHandleItWasScheduledWith() {
        Map<UUID, TaskHandle> running = new ConcurrentHashMap<>();
        UUID player = UUID.randomUUID();
        TaskHandle returned = scheduler.entityTimer(entity, Duration.ZERO, Duration.ofSeconds(1), handle -> {
            running.remove(player, handle);
        });
        running.put(player, returned);

        // Paper runs the task with its own ScheduledTask, which the scheduler wraps anew on every run.
        runs.get(0).accept(scheduled);

        assertThat(running).isEmpty();
    }

    @Test
    void handlesOnDifferentTasksDiffer() {
        TaskHandle first = scheduler.entityTimer(entity, Duration.ZERO, Duration.ofSeconds(1), handle -> {});
        TaskHandle second = new PaperTaskHandle(proxy(ScheduledTask.class, (method, args) -> null));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void handlesOnWorkThatNeverStartedDiffer() {
        assertThat(new PaperTaskHandle(null)).isNotEqualTo(new PaperTaskHandle(null));
    }

    private interface Answer {
        @Nullable Object answer(String method, @Nullable Object[] args);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(
                PaperSchedulerTest.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "toString" -> type.getSimpleName();
                        default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
                    };
                }));
    }
}
