package com.uxplima.uxmlib.storage.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.uxplima.uxmlib.storage.sql.Query;
import com.uxplima.uxmlib.storage.sql.RowMapper;
import com.uxplima.uxmlib.storage.sql.SelectBuilder;
import com.uxplima.uxmlib.storage.sql.Sql;

/**
 * The cross-server row-sync engine: on each {@link #pollOnce()} it asks the shared database for the rows a
 * <em>peer</em> changed since this poller last looked, and hands each to a {@link RowSyncListener} to apply
 * locally (refresh or evict the cache entry). It is the app-level alternative to database triggers: portable
 * across dialects because the version/updated-by columns are stamped by the application, not the DB clock.
 *
 * <p>The poll is {@code WHERE version > cursor AND updated_by != nodeId ORDER BY version ASC LIMIT batch}: only
 * rows newer than the high-water {@link #cursor()} (so a row is seen once) and not written by this node (so a
 * node never echoes its own writes). The cursor advances to the highest version the batch returned even for
 * rows that were skipped or threw, so one poisoned or locally-dirty row can never wedge the poller into
 * re-fetching it forever. A {@link #skipWhenDirty(Predicate) dirty guard} lets a caller refuse to clobber an
 * unsaved local edit: a peer's version for a dirty key is dropped (its version still counts toward the cursor).
 *
 * <p>This class does no scheduling and touches no Bukkit API; drive it from {@link RowSyncService}, which
 * repeats {@link #pollOnce()} on the library {@code Scheduler}'s async timer. Not thread-safe: a single poller
 * is meant to be ticked by one timer; the cursor is plain mutable state guarded by that single-threaded tick.
 *
 * @param <T> the mapped value type a row is turned into for the listener
 */
public final class RowSyncPoller<T> {

    private static final System.Logger LOG = System.getLogger(RowSyncPoller.class.getName());

    private final Sql sql;
    private final RowSyncConfig config;
    private final RowMapper<T> mapper;
    private final RowSyncListener<T> listener;
    private Predicate<Object> dirty = key -> false;
    private long cursor;

    /**
     * Create a poller over {@code config}'s table. {@code mapper} turns a changed row into the value the
     * {@code listener} applies; it reads the table's own columns (the poller selects every column) and must not
     * advance the cursor, exactly as a {@link RowMapper} elsewhere.
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public RowSyncPoller(Sql sql, RowSyncConfig config, RowMapper<T> mapper, RowSyncListener<T> listener) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.cursor = config.startCursor();
    }

    /**
     * Refuse to apply a peer's change to a key the {@code dirty} predicate marks as locally modified, so an
     * unsaved local edit is never clobbered by an in-flight remote write. The skipped row's version still
     * advances the cursor (the poller does not retry it forever); persist the local edit and the next remote
     * bump past it will win. Replaces any previously set guard.
     *
     * @throws NullPointerException if {@code dirty} is {@code null}
     */
    public void skipWhenDirty(Predicate<Object> dirty) {
        this.dirty = Objects.requireNonNull(dirty, "dirty");
    }

    /** The highest version this poller has consumed; the next poll fetches strictly above it. */
    public long cursor() {
        return cursor;
    }

    /**
     * Fetch the next batch of peer-changed rows above the cursor and apply each through the listener, returning
     * how many rows were applied (skips and listener failures do not count). The cursor advances to the batch's
     * highest version regardless, so every row is offered exactly once.
     *
     * <p>The cursor advances with {@code version > cursor}; like keyset pagination, that assumes versions are
     * effectively unique. If two rows carry the <em>same</em> version and that value lands on a {@code batchLimit}
     * boundary, the row pushed to the next page is dropped. Stamp a strictly increasing version (a per-row
     * sequence, or a clock fine enough that concurrent writers do not collide) to avoid it.
     */
    public int pollOnce() {
        Query query = changedRowsQuery();
        List<Mapped<T>> rows = sql.query(query, this::readRow);
        int applied = 0;
        for (Mapped<T> row : rows) {
            if (row.version > cursor) {
                cursor = row.version;
            }
            if (applyRow(row)) {
                applied++;
            }
        }
        return applied;
    }

    private Query changedRowsQuery() {
        return SelectBuilder.from(config.table())
                .where(config.versionColumn(), ">", cursor)
                .where(config.updatedByColumn(), "!=", config.nodeId())
                .orderBy(config.versionColumn())
                .limit(config.batchLimit())
                .build();
    }

    private boolean applyRow(Mapped<T> row) {
        if (dirty.test(row.key)) {
            // The local copy has an unsaved edit; dropping the remote value avoids clobbering it. The cursor
            // already moved past this version, so we don't keep re-fetching it once the local edit persists.
            return false;
        }
        try {
            listener.apply(new ChangedRow<>(row.key, row.version, row.value));
            return true;
        } catch (RuntimeException failure) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "row-sync listener failed for key " + row.key + " on " + config.table(),
                    failure);
            return false;
        }
    }

    private Mapped<T> readRow(ResultSet row) throws SQLException {
        Object key = row.getObject(config.keyColumn());
        long version = row.getLong(config.versionColumn());
        T value = mapper.map(row);
        return new Mapped<>(key, version, value);
    }

    /** The three things a poll reads from each row: its key, its version (cursor fuel), and the mapped value. */
    private record Mapped<T>(Object key, long version, T value) {}
}
