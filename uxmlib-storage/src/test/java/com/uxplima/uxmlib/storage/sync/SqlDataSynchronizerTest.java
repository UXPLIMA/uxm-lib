package com.uxplima.uxmlib.storage.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Two nodes on one database, telling each other what changed, on every backend a store can name. */
final class SqlDataSynchronizerTest {

    private static final String TABLE = "t_sync";
    private static final Duration KEEP = Duration.ofMinutes(5);

    private final List<Database> opened = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeEverything() {
        opened.forEach(Database::close);
    }

    @EveryBackend
    @DisplayName("a message reaches the other node on its next poll, and never the node that wrote it")
    void aMessageCrossesOnce(RealDatabase real) {
        SqlDataSynchronizer one = node(real, "one", Clock.systemUTC());
        SqlDataSynchronizer two = node(real, "two", Clock.systemUTC());
        List<String> heardByOne = new CopyOnWriteArrayList<>();
        List<String> heardByTwo = new CopyOnWriteArrayList<>();
        one.subscribe("tags", heardByOne::add);
        two.subscribe("tags", heardByTwo::add);

        one.publish("tags", "00000000-0000-0000-0000-0000000000a1");
        one.poll();
        two.poll();
        two.poll();

        assertThat(heardByTwo).containsExactly("00000000-0000-0000-0000-0000000000a1");
        assertThat(heardByOne).describedAs("a node read back what it wrote").isEmpty();
    }

    @EveryBackend
    @DisplayName("a channel's message goes to that channel's handlers only, and an unsubscribed handler hears nothing")
    void channelsAreApart(RealDatabase real) {
        SqlDataSynchronizer one = node(real, "one", Clock.systemUTC());
        SqlDataSynchronizer two = node(real, "two", Clock.systemUTC());
        List<String> tags = new CopyOnWriteArrayList<>();
        List<String> keys = new CopyOnWriteArrayList<>();
        two.subscribe("tags", tags::add);
        Subscription leaving = two.subscribe("keys", keys::add);
        leaving.unsubscribe();

        one.publish("tags", "a");
        one.publish("keys", "b");
        two.poll();

        assertThat(tags).containsExactly("a");
        assertThat(keys).isEmpty();
    }

    @EveryBackend
    @DisplayName("a node that starts later is not told what was said before it")
    void aLateNodeHearsNothingOld(RealDatabase real) {
        SqlDataSynchronizer one = node(real, "one", Clock.systemUTC());
        one.publish("tags", "old");

        SqlDataSynchronizer late = node(real, "late", Clock.systemUTC());
        List<String> heard = new CopyOnWriteArrayList<>();
        late.subscribe("tags", heard::add);
        one.publish("tags", "new");
        late.poll();

        assertThat(heard).containsExactly("new");
    }

    @EveryBackend
    @DisplayName("a handler that throws stops neither the others nor the cursor")
    void aFailingHandlerIsContained(RealDatabase real) {
        SqlDataSynchronizer one = node(real, "one", Clock.systemUTC());
        SqlDataSynchronizer two = node(real, "two", Clock.systemUTC());
        List<String> heard = new CopyOnWriteArrayList<>();
        two.subscribe("tags", message -> {
            throw new IllegalStateException("broken");
        });
        two.subscribe("tags", heard::add);

        one.publish("tags", "a");
        two.poll();
        two.poll();

        assertThat(heard).containsExactly("a");
    }

    @EveryBackend
    @DisplayName("a poll deletes what is older than the keep window, so the table stays small")
    void oldRowsAreSwept(RealDatabase real) {
        Clock noon = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
        Clock later = Clock.offset(noon, KEEP.plusSeconds(1));
        SqlDataSynchronizer one = node(real, "one", noon);
        SqlDataSynchronizer two = node(real, "two", later);
        one.publish("tags", "a");

        two.poll();

        assertThat(rows(real)).isZero();
    }

    @Test
    @DisplayName("a table name that is not a bare identifier is refused before any SQL is built")
    void aTableNameCannotCarrySql() {
        assertThatThrownBy(() -> new SqlDataSynchronizer(
                        Database.builder().sqliteInMemory().build(), "t; DROP TABLE x", "one", KEEP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SqlDataSynchronizer node(RealDatabase real, String id, Clock clock) {
        Database database = real.open();
        opened.add(database);
        return new SqlDataSynchronizer(database, TABLE, id, KEEP, clock);
    }

    private int rows(RealDatabase real) {
        Database database = real.open();
        opened.add(database);
        return new Sql(database)
                .queryFirst(
                        "SELECT COUNT(*) AS rows_here FROM " + TABLE, statement -> {}, row -> row.getInt("rows_here"))
                .orElse(-1);
    }
}
