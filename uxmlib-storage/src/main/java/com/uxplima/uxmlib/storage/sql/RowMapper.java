package com.uxplima.uxmlib.storage.sql;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the current row of a {@link ResultSet} to a value. Implementations read columns from the row but
 * must not advance, close, or otherwise mutate the cursor: {@link Sql} drives iteration.
 */
@FunctionalInterface
public interface RowMapper<T> {

    /** Build a value from the {@link ResultSet}'s current row. */
    T map(ResultSet row) throws SQLException;
}
