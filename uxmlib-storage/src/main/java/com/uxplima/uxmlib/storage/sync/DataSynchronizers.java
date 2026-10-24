package com.uxplima.uxmlib.storage.sync;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.StorageSettings;

/** The {@link DataSynchronizer} a configured store calls for. */
public final class DataSynchronizers {

    /** How long a notice is kept for a node that polls late. */
    public static final Duration KEEP_FOR = Duration.ofMinutes(5);

    private DataSynchronizers() {}

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
