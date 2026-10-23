package com.uxplima.uxmlib.storage.sync;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.jspecify.annotations.Nullable;

/**
 * A {@link DataSynchronizer} over a table in the database the nodes already share: a network with no Redis
 * still has its database, because that is what makes the rows shared.
 *
 * <p>A message is one row: the channel, the payload, the node that wrote it and when. Each {@link #poll()}
 * reads the rows after the last one this node read that another node wrote, hands each to the handlers of its
 * channel, and deletes the rows older than the keep window. A node never reads its own messages back.
 *
 * <p>A fresh node starts at the newest row and reads nothing before it: a node that starts at noon has no use
 * for what was said at ten. A node that was down longer than the keep window misses what it missed, so a
 * message is a hint to read the rows again, never the only copy of a change.
 *
 * <p>{@link #publish} writes on the calling thread, so it is called off the server thread, where the write
 * it announces was made. The table is created when the synchronizer is built, if it is not there yet.
 *
 * <p>Ids are handed out by the database. Two writers that commit out of order can leave a lower id visible
 * after a higher one was read, and that one message is then skipped. The window is the length of one insert,
 * and it is one more reason a message is a hint and not a record.
 */
public final class SqlDataSynchronizer implements DataSynchronizer {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int CHANNEL_LENGTH = 128;
    private static final int PAYLOAD_LENGTH = 1024;
    private static final int NODE_LENGTH = 64;
    private static final int BATCH = 500;

    private final Sql sql;
    private final String nodeId;
    private final Duration keepFor;
    private final Clock clock;
    private final String insertSql;
    private final String pollSql;
    private final String sweepSql;
    private final String newestSql;
    private final Map<String, CopyOnWriteArrayList<Consumer<String>>> channels = new ConcurrentHashMap<>();

    private volatile long lastSeen;
    private volatile boolean closed;
    private @Nullable TaskHandle timer;

    /**
     * A synchronizer on {@code table}, creating it when it is missing.
     *
     * @param table the full table name, prefix included: a bare SQL identifier
     * @param nodeId this node's id, which its own rows carry so it never reads them back
     * @param keepFor how long a message is kept for a node that polls late
     * @throws IllegalArgumentException if {@code table} is not a bare identifier, {@code nodeId} is blank or
     *     longer than 64 characters, or {@code keepFor} is negative
     */
    public SqlDataSynchronizer(Database database, String table, String nodeId, Duration keepFor) {
        this(database, table, nodeId, keepFor, Clock.systemUTC());
    }

    /** The same, reading the time from {@code clock}. */
    public SqlDataSynchronizer(Database database, String table, String nodeId, Duration keepFor, Clock clock) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(table, "table");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.keepFor = Objects.requireNonNull(keepFor, "keepFor");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!IDENTIFIER.matcher(table).matches()) {
            throw new IllegalArgumentException("table must be a bare SQL identifier, got: " + table);
        }
        if (nodeId.isBlank() || nodeId.length() > NODE_LENGTH) {
            throw new IllegalArgumentException("nodeId must be 1 to " + NODE_LENGTH + " characters, got: " + nodeId);
        }
        if (keepFor.isNegative()) {
            throw new IllegalArgumentException("keepFor must not be negative, got: " + keepFor);
        }
        this.sql = new Sql(database);
        this.insertSql = "INSERT INTO " + table + " (channel, payload, sender, sent_at) VALUES (?, ?, ?, ?)";
        this.pollSql = "SELECT id, channel, payload FROM " + table
                + " WHERE id > ? AND sender <> ? ORDER BY id ASC LIMIT " + BATCH;
        this.sweepSql = "DELETE FROM " + table + " WHERE sent_at < ?";
        this.newestSql = "SELECT COALESCE(MAX(id), 0) AS newest FROM " + table;
        sql.execute(createTable(database.dialect(), table));
        this.lastSeen = newest();
    }

    /**
     * Write {@code message} for the other nodes. Delivered to none of this node's own handlers.
     *
     * @throws IllegalArgumentException if the channel is longer than 128 characters or the message longer
     *     than 1024
     */
    @Override
    public void publish(String channel, String message) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        if (channel.length() > CHANNEL_LENGTH) {
            throw new IllegalArgumentException("a channel is at most " + CHANNEL_LENGTH + " characters");
        }
        if (message.length() > PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("a message is at most " + PAYLOAD_LENGTH + " characters");
        }
        if (closed) {
            return;
        }
        long now = clock.millis();
        sql.update(insertSql, statement -> {
            statement.setString(1, channel);
            statement.setString(2, message);
            statement.setString(3, nodeId);
            statement.setLong(4, now);
        });
    }

    @Override
    public Subscription subscribe(String channel, Consumer<String> handler) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        if (closed) {
            throw new IllegalStateException("synchronizer is closed");
        }
        channels.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> channels.computeIfPresent(channel, (key, handlers) -> {
            handlers.remove(handler);
            return handlers.isEmpty() ? null : handlers;
        });
    }

    /**
     * Read what the other nodes wrote since the last poll, hand it to the handlers, and delete what is older
     * than the keep window. Called by the timer {@link #start} sets up, or directly.
     *
     * @return how many messages were read
     */
    public synchronized int poll() {
        if (closed) {
            return 0;
        }
        List<Row> rows = sql.query(
                pollSql,
                statement -> {
                    statement.setLong(1, lastSeen);
                    statement.setString(2, nodeId);
                },
                row -> new Row(row.getLong("id"), row.getString("channel"), row.getString("payload")));
        for (Row row : rows) {
            lastSeen = Math.max(lastSeen, row.id());
            deliver(row);
        }
        long cutoff = clock.millis() - keepFor.toMillis();
        sql.update(sweepSql, statement -> statement.setLong(1, cutoff));
        return rows.size();
    }

    /**
     * Poll every {@code every} on the scheduler's async timer. A poll that fails is skipped: the rows stay
     * and the next poll reads them.
     *
     * @throws IllegalStateException if already started or closed
     */
    public synchronized void start(Scheduler scheduler, Duration every) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(every, "every");
        if (every.isZero() || every.isNegative()) {
            throw new IllegalArgumentException("every must be positive, got: " + every);
        }
        if (closed || timer != null) {
            throw new IllegalStateException("synchronizer is already started or closed");
        }
        timer = scheduler.asyncTimer(every, every, handle -> pollQuietly());
    }

    /** Stop the timer and drop every subscription. Idempotent. */
    @Override
    public synchronized void close() {
        closed = true;
        TaskHandle running = timer;
        if (running != null) {
            running.cancel();
            timer = null;
        }
        channels.clear();
    }

    private void pollQuietly() {
        try {
            poll();
        } catch (RuntimeException failed) {
            System.getLogger(SqlDataSynchronizer.class.getName())
                    .log(System.Logger.Level.WARNING, "A poll of the shared message table failed", failed);
        }
    }

    private void deliver(Row row) {
        List<Consumer<String>> handlers = channels.get(row.channel());
        if (handlers == null) {
            return;
        }
        for (Consumer<String> handler : handlers) {
            try {
                handler.accept(row.payload());
            } catch (RuntimeException failed) {
                // One handler never stops the others, and never stops the cursor: the row is read once.
                System.getLogger(SqlDataSynchronizer.class.getName())
                        .log(System.Logger.Level.WARNING, "A handler on " + row.channel() + " failed", failed);
            }
        }
    }

    private long newest() {
        return sql.queryFirst(newestSql, statement -> {}, row -> row.getLong("newest"))
                .orElse(0L);
    }

    /** The table, in the spelling of each engine's own counter column. */
    static String createTable(Dialect dialect, String table) {
        String id =
                switch (dialect) {
                    case SQLITE -> "id INTEGER PRIMARY KEY AUTOINCREMENT";
                    case MYSQL -> "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
                    case POSTGRES -> "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
                    case H2, GENERIC -> "id BIGINT AUTO_INCREMENT PRIMARY KEY";
                };
        return "CREATE TABLE IF NOT EXISTS " + table + " (" + id + ", channel VARCHAR(" + CHANNEL_LENGTH
                + ") NOT NULL, payload VARCHAR(" + PAYLOAD_LENGTH + ") NOT NULL, sender VARCHAR(" + NODE_LENGTH
                + ") NOT NULL, sent_at BIGINT NOT NULL)";
    }

    private record Row(long id, String channel, String payload) {}
}
