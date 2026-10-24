package com.uxplima.uxmlib.storage.sync;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmlib.config.HoconConfig;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.StorageSettings;

/** The {@link DataSynchronizer} a configured store calls for. */
public final class DataSynchronizers {

    /** How often a node reads the notices when the block says nothing. */
    public static final Duration DEFAULT_EVERY = Duration.ofSeconds(2);

    /** How long a notice is kept for a node that polls late. */
    public static final Duration KEEP_FOR = Duration.ofMinutes(5);

    private DataSynchronizers() {}

    /**
     * How often to read the other nodes' notices: {@code <path>.sync-seconds} of {@code config}, two seconds when
     * it is not written, and zero, which turns them off, for zero or less. One key, so every plugin's storage
     * block says it the same way.
     */
    public static Duration every(HoconConfig config, String path) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        double seconds = config.getDouble(path + ".sync-seconds", DEFAULT_EVERY.toMillis() / 1000.0d);
        return seconds <= 0 ? Duration.ZERO : Duration.ofMillis(Math.round(seconds * 1000.0d));
    }

    /**
     * A synchronizer for the nodes that share {@code storage}, polling every {@code every}.
     *
     * <p>A SQLite file is one server's, so it gets the in-process bus and no timer. A database server may be
     * shared, so it gets {@link SqlDataSynchronizer} on {@code <prefix>sync}, started on the scheduler's async
     * timer, under a node id of its own for this start. A zero interval turns the notices off.
     */
    public static DataSynchronizer forStore(
            StorageSettings storage, Database database, Scheduler scheduler, Duration every) {
        Objects.requireNonNull(scheduler, "scheduler");
        return forStore(
                storage, database, (period, tick) -> scheduler.asyncTimer(period, period, handle -> tick.run()), every);
    }

    // The seam the storage tests drive, since they run no server to back a real Scheduler.
    static DataSynchronizer forStore(
            StorageSettings storage, Database database, RowSyncService.RepeatingTimer timer, Duration every) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(timer, "timer");
        Objects.requireNonNull(every, "every");
        if (storage.backend().isFile() || every.isZero() || every.isNegative()) {
            return new LocalDataSynchronizer();
        }
        SqlDataSynchronizer shared = new SqlDataSynchronizer(
                database, storage.table("sync"), UUID.randomUUID().toString(), KEEP_FOR);
        shared.start(timer, every);
        return shared;
    }
}
