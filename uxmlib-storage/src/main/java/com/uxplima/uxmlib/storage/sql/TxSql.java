package com.uxplima.uxmlib.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmlib.storage.StorageException;

/**
 * The {@link Sql}-shaped facade handed to a {@link Database#transaction} block: every query and update
 * runs on the <em>one</em> connection the transaction borrowed, so they share a unit of work that commits
 * together or rolls back together. Don't keep a reference past the block: the connection is returned to
 * the pool when it ends.
 */
public final class TxSql {

    private final Connection connection;

    TxSql(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Run a query on the transaction's connection and map every row. */
    public <T> List<T> query(String sql, StatementBinder binder, RowMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rows.next()) {
                    results.add(mapper.map(rows));
                }
                return List.copyOf(results);
            }
        } catch (SQLException failure) {
            throw new StorageException("query failed in transaction: " + sql, failure);
        }
    }

    /** Run a {@link Query} on the transaction's connection and map every row. */
    public <T> List<T> query(Query query, RowMapper<T> mapper) {
        Objects.requireNonNull(query, "query");
        return query(query.sql(), query.binder(), mapper);
    }

    /** Run a query and map the first row, if any. */
    public <T> Optional<T> queryFirst(String sql, StatementBinder binder, RowMapper<T> mapper) {
        List<T> results = query(sql, binder, mapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Run an INSERT/UPDATE/DELETE on the transaction's connection; returns the affected row count. */
    public int update(String sql, StatementBinder binder) {
        Objects.requireNonNull(sql, "sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new StorageException("update failed in transaction: " + sql, failure);
        }
    }

    /** Run a parameterless statement on the transaction's connection. */
    public void execute(String sql) {
        update(sql, StatementBinder.NONE);
    }
}
