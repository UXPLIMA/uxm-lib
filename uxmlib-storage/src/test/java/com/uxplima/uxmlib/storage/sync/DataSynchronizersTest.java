package com.uxplima.uxmlib.storage.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.config.HoconConfig;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    @DisplayName("the interval is read off the storage block, two seconds when unwritten, never below zero")
    void theIntervalIsRead(@TempDir Path folder) throws IOException {
        assertThat(DataSynchronizers.every(config(folder, ""), "storage")).isEqualTo(Duration.ofSeconds(2));
        assertThat(DataSynchronizers.every(config(folder, "storage { sync-seconds = 0.5 }"), "storage"))
                .isEqualTo(Duration.ofMillis(500));
        assertThat(DataSynchronizers.every(config(folder, "storage { sync-seconds = 0 }"), "storage"))
                .isZero();
        assertThat(DataSynchronizers.every(config(folder, "storage { sync-seconds = -3 }"), "storage"))
                .isZero();
    }

    private static HoconConfig config(Path folder, String text) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return HoconConfig.load(file);
    }
}
