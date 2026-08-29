package com.uxplima.uxmlib.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import com.uxplima.uxmlib.storage.StorageException;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A pooled database handle over HikariCP. Build one with {@link #builder()}; borrow connections with
 * {@link #connection()} (close them to return them to the pool) and shut the pool down with
 * {@link #close()}. The {@link DataSource} is exposed for callers that need to hand it to other
 * libraries. Created handles are independent; closing one does not affect another.
 */
public final class Database implements AutoCloseable {

    private final DataSource dataSource;
    private final Dialect dialect;
    private final boolean owned;

    Database(DataSource dataSource, Dialect dialect, boolean owned) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.owned = owned;
    }

    /** Start configuring a database; SQLite is the default backend. */
    public static DatabaseBuilder builder() {
        return new DatabaseBuilder();
    }

    /**
     * Wrap a {@link DataSource} the caller owns and manages so its queries can run through this library's
     * {@link Sql} / {@link TxSql} helpers and builders. The adopted handle does <em>not</em> own the pool:
     * {@link #close()} is a no-op, leaving the lifecycle to whoever created the DataSource. {@code dialect}
     * must match the backend, since it drives upsert and other dialect-specific SQL.
     */
    public static Database adopt(DataSource dataSource, Dialect dialect) {
        return new Database(dataSource, dialect, false);
    }

    /** Borrow a pooled connection. The caller closes it to return it to the pool. */
    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Run {@code work} as a single transaction: one connection with auto-commit off, committed if the
     * function returns normally and rolled back if it throws, then the connection is restored and returned
     * to the pool. Every query/update on the supplied {@link TxSql} shares that connection.
     */
    public <R> R transaction(java.util.function.Function<TxSql, R> work) {
        Objects.requireNonNull(work, "work");
        try (Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                R result = work.apply(new TxSql(conn));
                conn.commit();
                return result;
            } catch (RuntimeException failure) {
                conn.rollback();
                throw failure;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new StorageException("transaction failed", failure);
        }
    }

    /** Run {@code work} as a transaction with no return value. */
    public void inTransaction(java.util.function.Consumer<TxSql> work) {
        Objects.requireNonNull(work, "work");
        transaction(tx -> {
            work.accept(tx);
            return null;
        });
    }

    /** The underlying pooled {@link DataSource}. */
    public DataSource dataSource() {
        return dataSource;
    }

    /** The SQL dialect this database speaks, inferred from its JDBC URL (drives upsert and the like). */
    public Dialect dialect() {
        return dialect;
    }

    /** Whether the pool has been shut down (always false for an adopted, non-Hikari, or external DataSource). */
    public boolean isClosed() {
        return dataSource instanceof HikariDataSource hikari && hikari.isClosed();
    }

    /**
     * Whether the database actually answers: borrows a connection and runs {@code SELECT 1}, returning
     * false on any failure. Unlike {@link #isClosed()} (which only reports pool shutdown) this reports
     * reachability, for an operator "doctor" check.
     */
    public boolean ping() {
        try (Connection conn = dataSource.getConnection();
                java.sql.Statement statement = conn.createStatement();
                java.sql.ResultSet rows = statement.executeQuery("SELECT 1")) {
            return rows.next();
        } catch (SQLException unreachable) {
            return false;
        }
    }

    /**
     * Shut the pool down, closing all idle connections. Safe to call more than once. A no-op for an adopted
     * handle ({@link #adopt}): that pool belongs to its creator.
     */
    @Override
    public void close() {
        if (owned && dataSource instanceof HikariDataSource hikari && !hikari.isClosed()) {
            hikari.close();
        }
    }
}
