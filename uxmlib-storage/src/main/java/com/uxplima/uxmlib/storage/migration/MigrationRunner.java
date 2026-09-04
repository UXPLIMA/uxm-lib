package com.uxplima.uxmlib.storage.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;

/**
 * Applies a set of {@link Migration}s exactly once each, in ascending version order. Applied versions are
 * recorded in a history table, so re-running {@link #apply(List)} is idempotent: already-applied migrations
 * are skipped and only newer ones run. Each migration runs in its own transaction: a failure rolls that
 * migration back and aborts, leaving the schema at the last good version. This is intentionally simpler than
 * Flyway: no checksums, no out-of-order handling.
 *
 * <p>The history table defaults to {@code uxmlib_schema_history}, which is one table for every consumer of a
 * database. Give it a name of your own whenever two schemas can share a backend: a plugin that lets an
 * operator choose a table prefix, or two plugins pointed at one MySQL. With one shared history the second
 * schema is told that every migration has run, creates nothing, and fails at its first query.
 */
public final class MigrationRunner {

    /** The history table a runner uses when it is given no name: one table for every consumer. */
    public static final String DEFAULT_HISTORY_TABLE = "uxmlib_schema_history";

    // The table name is inlined into DDL, so it is held to a bare identifier and can carry no injection.
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Database database;
    private final String historyTable;

    public MigrationRunner(Database database) {
        this(database, DEFAULT_HISTORY_TABLE);
    }

    /**
     * A runner that records what it applied in {@code historyTable}.
     *
     * @param historyTable a bare SQL identifier, created when it does not exist
     */
    public MigrationRunner(Database database, String historyTable) {
        this.database = Objects.requireNonNull(database, "database");
        Objects.requireNonNull(historyTable, "historyTable");
        if (!TABLE_NAME.matcher(historyTable).matches()) {
            throw new IllegalArgumentException(
                    "historyTable must be a simple SQL identifier (got '" + historyTable + "')");
        }
        this.historyTable = historyTable;
    }

    /** The table this runner records applied versions in. */
    public String historyTable() {
        return historyTable;
    }

    /** Apply every migration whose version exceeds the highest already applied. Returns how many ran. */
    public int apply(List<Migration> migrations) {
        Objects.requireNonNull(migrations, "migrations");
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparingInt(Migration::version));
        try (Connection conn = database.connection()) {
            ensureHistoryTable(conn);
            int current = currentVersion(conn);
            int applied = 0;
            for (Migration migration : ordered) {
                if (migration.version() > current) {
                    applyOne(conn, migration);
                    applied++;
                }
            }
            return applied;
        } catch (SQLException failure) {
            throw new StorageException("migration failed", failure);
        }
    }

    /** The highest applied migration version, or 0 when none have run. */
    public int currentVersion() {
        try (Connection conn = database.connection()) {
            ensureHistoryTable(conn);
            return currentVersion(conn);
        } catch (SQLException failure) {
            throw new StorageException("could not read schema version", failure);
        }
    }

    private void ensureHistoryTable(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + historyTable
                    + " (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at_ms BIGINT NOT NULL)");
        }
        widenAppliedAt(conn);
    }

    /**
     * Widen {@code applied_at_ms} on a history table written by an earlier version of this class, which
     * declared it {@code INTEGER}: eight bytes on SQLite, four on MySQL, and a moment in milliseconds has not
     * fitted in four bytes since 1970.
     */
    private void widenAppliedAt(Connection conn) throws SQLException {
        if (database.dialect() != Dialect.MYSQL || !isFourByteColumn(conn)) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute("ALTER TABLE " + historyTable + " MODIFY applied_at_ms BIGINT NOT NULL");
        }
    }

    private boolean isFourByteColumn(Connection conn) throws SQLException {
        try (ResultSet columns = conn.getMetaData().getColumns(null, null, historyTable, "applied_at_ms")) {
            return columns.next() && columns.getInt("DATA_TYPE") == java.sql.Types.INTEGER;
        }
    }

    private int currentVersion(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM " + historyTable)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private void applyOne(Connection conn, Migration migration) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (String statementSql : splitStatements(migration.sql())) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            recordApplied(conn, migration);
            conn.commit();
        } catch (SQLException failure) {
            conn.rollback();
            throw new SQLException(
                    "migration " + migration.version() + " (" + migration.description() + ") failed", failure);
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private void recordApplied(Connection conn, Migration migration) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO " + historyTable + " (version, description, applied_at_ms) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static List<String> splitStatements(String sql) {
        // Split on ';' by scanning, but only when the ';' is real SQL, not inside a '...' string literal,
        // a "..." quoted identifier, a -- line comment, or a /* */ block comment. A naive split would turn
        // INSERT ... VALUES ('a;b') into two broken fragments. String.split/Pattern.split are avoided both
        // for that reason and because ErrorProne flags their trailing-empty behaviour. Blank statements drop.
        List<String> statements = new ArrayList<>();
        int start = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
            } else if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
            } else if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                }
            } else if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                }
            } else if (c == '-' && next == '-') {
                inLineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else if (c == '\'') {
                inSingle = true;
            } else if (c == '"') {
                inDouble = true;
            } else if (c == ';') {
                addStatement(statements, sql, start, i);
                start = i + 1;
            }
        }
        addStatement(statements, sql, start, sql.length());
        return statements;
    }

    private static void addStatement(List<String> statements, String sql, int start, int end) {
        String trimmed = sql.substring(start, end).strip();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed);
        }
    }
}
