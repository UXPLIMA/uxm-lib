package com.uxplima.uxmlib.storage.sync;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The schema contract a {@link RowSyncPoller} polls against, made explicit because cross-server row-sync only
 * works if the table carries two extra, application-stamped columns:
 *
 * <ul>
 *   <li>a <b>version</b> column: a monotonically increasing {@code long} the writer bumps on every change
 *       (a logical clock or {@code System.currentTimeMillis()}); the poller seeks on {@code version > cursor}
 *       to find what a peer touched since it last looked;</li>
 *   <li>an <b>updated-by</b> column: the id of the node that last wrote the row, so a node can skip the
 *       changes it made itself ({@code updated_by != nodeId}) and never echo its own writes back into cache.</li>
 * </ul>
 *
 * <p>The columns are <em>stamped by the application</em>, not by a database trigger or {@code CURRENT_TIMESTAMP}:
 * that keeps the mechanism portable across SQLite/H2/MySQL/Postgres rather than tied to one dialect's clock.
 * Wire the version/updated-by bump into your own write path; this type only describes where to read them.
 *
 * <p>All four column names and the node id are required; {@link #batchLimit()} caps how many changed rows a
 * single poll applies (the remainder follow on the next tick) and {@link #startCursor()} sets the version a
 * fresh poller begins above (default {@code 0}: apply everything once on first run).
 */
public final class RowSyncConfig {

    // A bare SQL identifier the poller is willing to inline into ORDER BY / column lists. Anything fancier
    // (a quoted or dotted name) is rejected so a config value can never smuggle SQL into the polling query.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final String table;
    private final String keyColumn;
    private final String versionColumn;
    private final String updatedByColumn;
    private final String nodeId;
    private final int batchLimit;
    private final long startCursor;

    private RowSyncConfig(Builder builder) {
        this.table = builder.table;
        this.keyColumn = builder.keyColumn;
        this.versionColumn = builder.versionColumn;
        this.updatedByColumn = builder.updatedByColumn;
        this.nodeId = builder.nodeId;
        this.batchLimit = builder.batchLimit;
        this.startCursor = builder.startCursor;
    }

    /**
     * Begin configuring a poller over {@code table}. Each of the four columns must be a simple SQL identifier;
     * {@code nodeId} is this server's stable id (the value its own writes stamp into {@code updatedByColumn}).
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any column is not a simple SQL identifier, or {@code nodeId} is blank
     */
    public static Builder builder(
            String table, String keyColumn, String versionColumn, String updatedByColumn, String nodeId) {
        return new Builder(table, keyColumn, versionColumn, updatedByColumn, nodeId);
    }

    /** The table being synchronised. */
    public String table() {
        return table;
    }

    /** The primary-key column (the row identity handed to the listener and the dirty check). */
    public String keyColumn() {
        return keyColumn;
    }

    /** The monotonically increasing version column the poll seeks on. */
    public String versionColumn() {
        return versionColumn;
    }

    /** The column naming the node that last wrote the row, so a node skips its own writes. */
    public String updatedByColumn() {
        return updatedByColumn;
    }

    /** This node's id; rows whose {@code updatedByColumn} equals it are filtered out of every poll. */
    public String nodeId() {
        return nodeId;
    }

    /** The maximum number of changed rows one poll applies before yielding to the next tick. */
    public int batchLimit() {
        return batchLimit;
    }

    /** The version a fresh poller starts above; defaults to {@code 0} (apply every existing row once). */
    public long startCursor() {
        return startCursor;
    }

    /** Builds a {@link RowSyncConfig}; see {@link RowSyncConfig#builder}. */
    public static final class Builder {

        private static final int DEFAULT_BATCH_LIMIT = 500;

        private final String table;
        private final String keyColumn;
        private final String versionColumn;
        private final String updatedByColumn;
        private final String nodeId;
        private int batchLimit = DEFAULT_BATCH_LIMIT;
        private long startCursor;

        private Builder(String table, String keyColumn, String versionColumn, String updatedByColumn, String nodeId) {
            this.table = identifier(table, "table");
            this.keyColumn = identifier(keyColumn, "keyColumn");
            this.versionColumn = identifier(versionColumn, "versionColumn");
            this.updatedByColumn = identifier(updatedByColumn, "updatedByColumn");
            this.nodeId = requireNonBlank(nodeId);
            rejectDuplicateColumns(Set.of(this.keyColumn, this.versionColumn, this.updatedByColumn));
        }

        /** Cap how many changed rows a single poll applies; the rest follow on the next tick. Must be {@code >= 1}. */
        public Builder batchLimit(int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("batchLimit must be >= 1 (got " + limit + ")");
            }
            this.batchLimit = limit;
            return this;
        }

        /** The version a fresh poll starts strictly above; rows at or below it are not re-applied. Must be {@code >= 0}. */
        public Builder startCursor(long version) {
            if (version < 0) {
                throw new IllegalArgumentException("startCursor must be >= 0 (got " + version + ")");
            }
            this.startCursor = version;
            return this;
        }

        /** Build the immutable config. */
        public RowSyncConfig build() {
            return new RowSyncConfig(this);
        }

        private static String identifier(String value, String what) {
            Objects.requireNonNull(value, what);
            if (!IDENTIFIER.matcher(value).matches()) {
                throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value + "')");
            }
            return value;
        }

        private static String requireNonBlank(String nodeId) {
            Objects.requireNonNull(nodeId, "nodeId");
            if (nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId must not be blank");
            }
            return nodeId;
        }

        private static void rejectDuplicateColumns(Set<String> distinct) {
            if (distinct.size() != 3) {
                throw new IllegalArgumentException("key, version and updated-by columns must be distinct");
            }
        }
    }
}
