package com.uxplima.uxmlib.storage.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;
import org.junit.jupiter.api.DisplayName;

/** The synchronizer a store gets: the shared database when there is one to share, the process otherwise. */
final class DataSynchronizersTest {

    @EveryBackend
    @DisplayName("a file stays in the process, a database server carries the notices, and zero turns them off")
    void theStoreDecides(RealDatabase real) {
        List<Duration> scheduled = new ArrayList<>();
        RowSyncService.RepeatingTimer scheduler = (period, tick) -> {
            scheduled.add(period);
            return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        };
        try (Database database = real.open()) {
            DataSynchronizer chosen =
                    DataSynchronizers.forStore(real.settings(), database, scheduler, Duration.ofSeconds(2));
            DataSynchronizer off = DataSynchronizers.forStore(real.settings(), database, scheduler, Duration.ZERO);

            if (real.backend().isFile()) {
                assertThat(chosen).isInstanceOf(LocalDataSynchronizer.class);
                assertThat(scheduled).isEmpty();
            } else {
                assertThat(chosen).isInstanceOf(SqlDataSynchronizer.class);
                assertThat(scheduled).containsExactly(Duration.ofSeconds(2));
            }
            assertThat(off).isInstanceOf(LocalDataSynchronizer.class);
            chosen.close();
            off.close();
        }
    }
}
