package com.uxplima.uxmlib.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackupParticipantsTest {

    private ExecutorService where;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        where = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        where.shutdownNow();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a registered plugin is asked to save, and it says nothing was late")
    void everyParticipantRuns() {
        Plugin plugin = MockBukkit.createMockPlugin("uxmCore");
        AtomicBoolean saved = new AtomicBoolean();
        BackupParticipants.register(plugin, () -> saved.set(true));

        List<String> late = BackupParticipants.prepareAll(Duration.ofSeconds(5), where);

        assertThat(saved).isTrue();
        assertThat(late).isEmpty();
        assertThat(BackupParticipants.listening()).containsExactly("uxmCore");
    }

    @Test
    @DisplayName("a plugin that will not finish is named, and the wait still ends")
    void aSlowParticipantIsNamedAndDoesNotHold() throws Exception {
        Plugin quick = MockBukkit.createMockPlugin("uxmQuick");
        Plugin slow = MockBukkit.createMockPlugin("uxmSlow");
        CountDownLatch held = new CountDownLatch(1);
        BackupParticipants.register(quick, () -> {});
        BackupParticipants.register(slow, () -> {
            try {
                held.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        List<String> late = BackupParticipants.prepareAll(Duration.ofMillis(200), where);
        held.countDown();

        assertThat(late).containsExactly("uxmSlow");
    }

    @Test
    @DisplayName("a runnable service registered for something else is never run")
    void aStrangerSRunnableIsLeftAlone() {
        Plugin stranger = MockBukkit.createMockPlugin("SomeOtherPlugin");
        AtomicBoolean touched = new AtomicBoolean();
        Bukkit.getServicesManager().register(Runnable.class, () -> touched.set(true), stranger, ServicePriority.Normal);

        List<String> late = BackupParticipants.prepareAll(Duration.ofSeconds(1), where);

        assertThat(touched).isFalse();
        assertThat(late).isEmpty();
        assertThat(BackupParticipants.listening()).isEmpty();
    }

    @Test
    @DisplayName("a plugin that unregisters is no longer asked")
    void unregisterStopsTheAsk() {
        Plugin plugin = MockBukkit.createMockPlugin("uxmCore");
        AtomicBoolean saved = new AtomicBoolean();
        BackupParticipants.register(plugin, () -> saved.set(true));
        BackupParticipants.unregister(plugin);

        Executor here = Runnable::run;
        assertThat(BackupParticipants.prepareAll(Duration.ofSeconds(1), here)).isEmpty();
        assertThat(saved).isFalse();
    }
}
