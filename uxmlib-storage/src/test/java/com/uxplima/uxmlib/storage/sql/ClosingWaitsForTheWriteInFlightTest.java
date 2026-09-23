package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Closing the database lets a write that is already running finish.
 *
 * <p>A server that stops kicks every player, and a plugin that saves a player on the way out hands that write to the
 * async lane. The plugin is disabled a moment later and closes its database. HikariCP aborts the connections still in
 * use when its pool closes, so on 2026-09-23 the question was whether that last write is lost. It is not: on SQLite
 * and on H2, whose driver really aborts a connection, the running transaction commits. Every plugin of the estate
 * saves that way and closes that way, so this holds the answer in place, and a HikariCP upgrade that changed it would
 * fail here first.
 */
class ClosingWaitsForTheWriteInFlightTest {

    private final ExecutorService lane = Executors.newSingleThreadExecutor();

    private String url = "";

    private Path folder = Path.of("");

    @BeforeEach
    void setUp(@TempDir Path folder) {
        this.folder = folder;
        use("jdbc:sqlite:" + folder.resolve("store.db"));
    }

    /** Point the test at a backend and give it the table. */
    private void use(String backend) {
        url = backend;
        try (Database database = open()) {
            new Sql(database).execute("CREATE TABLE saved (id INTEGER PRIMARY KEY)");
        }
    }

    @AfterEach
    void tearDown() {
        lane.shutdownNow();
    }

    @Test
    @DisplayName("a transaction running when the database closes is committed, not aborted")
    void aRunningWriteIsCommitted() throws Exception {
        assertTheRunningWriteSurvives();
    }

    /** H2 aborts a connection the way a network server's driver does, where SQLite has nothing to abort. */
    @Test
    @DisplayName("the same holds on a backend whose driver really aborts a connection")
    void aRunningWriteIsCommittedWhereAbortIsReal() throws Exception {
        use("jdbc:h2:file:" + folder.resolve("h2store").toAbsolutePath());
        assertTheRunningWriteSurvives();
    }

    private void assertTheRunningWriteSurvives() throws Exception {
        Database database = open();
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> write = CompletableFuture.runAsync(
                () -> database.inTransaction(tx -> {
                    tx.update("INSERT INTO saved (id) VALUES (1)", StatementBinder.NONE);
                    started.countDown();
                    pause(Duration.ofMillis(400));
                }),
                lane);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        database.close();

        write.get(5, TimeUnit.SECONDS);
        try (Database reopened = open()) {
            assertThat(new Sql(reopened).queryFirst("SELECT id FROM saved", StatementBinder.NONE, row -> row.getInt(1)))
                    .contains(1);
        }
    }

    @Test
    @DisplayName("a close with nothing running does not wait")
    void anIdleCloseIsImmediate() {
        Database database = open();
        long before = System.nanoTime();

        database.close();

        assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(1));
        assertThat(database.isClosed()).isTrue();
    }

    private Database open() {
        return Database.builder().jdbcUrl(url).maxPoolSize(2).build();
    }

    private static void pause(Duration length) {
        try {
            Thread.sleep(length);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
