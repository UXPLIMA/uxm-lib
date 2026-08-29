package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers transaction commit/rollback and the Scheduler-port async surface. */
class TransactionTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibtx?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY, balance INTEGER NOT NULL)");
        sql.update("INSERT INTO accounts (id, balance) VALUES (1, 100)", StatementBinder.NONE);
        sql.update("INSERT INTO accounts (id, balance) VALUES (2, 0)", StatementBinder.NONE);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private int balance(int id) {
        return sql.queryFirst("SELECT balance FROM accounts WHERE id = ?", ps -> ps.setInt(1, id), r -> r.getInt(1))
                .orElseThrow();
    }

    @Test
    void commitsBothWritesTogether() {
        database.inTransaction(tx -> {
            tx.update("UPDATE accounts SET balance = balance - 30 WHERE id = 1", StatementBinder.NONE);
            tx.update("UPDATE accounts SET balance = balance + 30 WHERE id = 2", StatementBinder.NONE);
        });
        assertThat(balance(1)).isEqualTo(70);
        assertThat(balance(2)).isEqualTo(30);
    }

    @Test
    void rollsBackEverythingOnThrow() {
        assertThatThrownBy(() -> database.inTransaction(tx -> {
                    tx.update("UPDATE accounts SET balance = balance - 30 WHERE id = 1", StatementBinder.NONE);
                    throw new IllegalStateException("boom"); // after the first write
                }))
                .isInstanceOf(IllegalStateException.class);
        // The debit was rolled back: nothing changed.
        assertThat(balance(1)).isEqualTo(100);
    }

    @Test
    void transactionReturnsAValue() {
        int total = database.transaction(
                tx -> tx.queryFirst("SELECT SUM(balance) FROM accounts", StatementBinder.NONE, r -> r.getInt(1))
                        .orElse(0));
        assertThat(total).isEqualTo(100);
    }

    @Test
    void asyncQueryRunsOnTheGivenExecutorAndCompletes() throws Exception {
        // A direct (same-thread) executor: the point is that we never use CompletableFuture.supplyAsync.
        Executor direct = Runnable::run;
        List<Integer> balances = sql.queryAsync(
                        direct, "SELECT balance FROM accounts ORDER BY id", StatementBinder.NONE, r -> r.getInt(1))
                .get();
        assertThat(balances).containsExactly(100, 0);
    }

    @Test
    void asyncPropagatesFailureExceptionally() {
        Executor direct = Runnable::run;
        var future = sql.queryAsync(direct, "SELECT * FROM nope", StatementBinder.NONE, r -> r.getInt(1));
        assertThat(future.isCompletedExceptionally()).isTrue();
    }
}
