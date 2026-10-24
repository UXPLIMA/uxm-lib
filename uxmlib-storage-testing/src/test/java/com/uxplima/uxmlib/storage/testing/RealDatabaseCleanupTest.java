package com.uxplima.uxmlib.storage.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.uxmlib.storage.sql.Backend;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** A database handed to a test leaves nothing on the disk once the test is over. */
class RealDatabaseCleanupTest {

    private static final List<Path> HANDED_OUT = new CopyOnWriteArrayList<>();

    @Test
    void aClosedDatabaseTakesItsFolderWithIt() {
        RealDatabase real = RealDatabases.fresh(Backend.SQLITE);
        try (Database database = real.open()) {
            new Sql(database).execute("CREATE TABLE t_rows (id INTEGER PRIMARY KEY)");
        }

        real.close();

        assertThat(real.dataFolder()).doesNotExist();
    }

    @EveryBackend
    void theRunClosesWhatItHandsOut(RealDatabase real) {
        HANDED_OUT.add(real.dataFolder());
        try (Database database = real.open()) {
            new Sql(database).execute("CREATE TABLE t_rows (id INTEGER PRIMARY KEY)");
        }
        assertThat(real.dataFolder()).exists();
    }

    @AfterAll
    static void nothingIsLeftBehind() {
        assertThat(HANDED_OUT)
                .isNotEmpty()
                .allSatisfy(folder -> assertThat(Files.exists(folder)).isFalse());
    }
}
