package com.uxplima.uxmlib.storage.sync;

/**
 * Applies a row a peer changed to the local node: typically refreshing or evicting the entry for
 * {@link ChangedRow#key()} in the caller's cache so the next read sees the peer's value. Invoked once per
 * changed row by {@link RowSyncPoller#pollOnce()} on the poller's (async) thread, so it must not touch the
 * Bukkit API directly; hop to a region/global task through the scheduler if it needs to.
 *
 * <p>A listener that throws on one row is logged and skipped: its failure never aborts the rest of the batch
 * and never wedges the cursor (see {@link RowSyncPoller}).
 *
 * @param <T> the mapped value type carried by the {@link ChangedRow}
 */
@FunctionalInterface
public interface RowSyncListener<T> {

    /** Apply {@code row} locally. */
    void apply(ChangedRow<T> row);
}
