package com.uxplima.uxmlib.storage.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the row-sync poller against a real in-memory SQLite table, the same way the SQL helpers are tested.
 * The poll step is exercised directly via {@link RowSyncPoller#pollOnce()} so the assertions are deterministic
 * and never depend on a timer firing: {@link RowSyncService} only repeats this same step on an async timer.
 */
class RowSyncPollerTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibrowsync?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players ("
                + "id INTEGER PRIMARY KEY, name TEXT NOT NULL, "
                + "row_version INTEGER NOT NULL, updated_by TEXT NOT NULL)");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private void insert(int id, String name, long version, String updatedBy) {
        sql.update("INSERT INTO players (id, name, row_version, updated_by) VALUES (?, ?, ?, ?)", ps -> {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setLong(3, version);
            ps.setString(4, updatedBy);
        });
    }

    private void touch(int id, String name, long version, String updatedBy) {
        sql.update("UPDATE players SET name = ?, row_version = ?, updated_by = ? WHERE id = ?", ps -> {
            ps.setString(1, name);
            ps.setLong(2, version);
            ps.setString(3, updatedBy);
            ps.setInt(4, id);
        });
    }

    private RowSyncConfig config() {
        return RowSyncConfig.builder("players", "id", "row_version", "updated_by", "node-A")
                .build();
    }

    private RowSyncPoller<String> poller(RowSyncConfig config, RowSyncListener<String> listener) {
        return new RowSyncPoller<>(sql, config, row -> row.getString("name"), listener);
    }

    @Test
    void appliesRowsAPeerChangedSinceTheCursor() {
        insert(1, "Steve", 5, "node-B");
        insert(2, "Alex", 7, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncPoller<String> poller = poller(config(), row -> applied.add(row.key() + "=" + row.value()));

        int count = poller.pollOnce();

        assertThat(count).isEqualTo(2);
        assertThat(applied).containsExactly("1=Steve", "2=Alex");
    }

    @Test
    void ignoresRowsThisNodeWroteItself() {
        insert(1, "Steve", 5, "node-A");
        insert(2, "Alex", 7, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncPoller<String> poller = poller(config(), row -> applied.add(row.value()));

        poller.pollOnce();

        // node-A's own write is filtered out; only the peer's row arrives.
        assertThat(applied).containsExactly("Alex");
    }

    @Test
    void advancesTheCursorSoASecondPollSeesOnlyNewerRows() {
        insert(1, "Steve", 5, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncPoller<String> poller = poller(config(), row -> applied.add(row.value()));

        assertThat(poller.pollOnce()).isEqualTo(1);
        assertThat(poller.pollOnce()).isZero();

        touch(1, "Steven", 9, "node-B");
        assertThat(poller.pollOnce()).isEqualTo(1);

        assertThat(applied).containsExactly("Steve", "Steven");
    }

    @Test
    void exposesTheHighWaterMarkVersion() {
        insert(1, "Steve", 5, "node-B");
        insert(2, "Alex", 7, "node-B");

        RowSyncPoller<String> poller = poller(config(), row -> {});

        assertThat(poller.cursor()).isZero();
        poller.pollOnce();
        assertThat(poller.cursor()).isEqualTo(7);
    }

    @Test
    void honoursAnExplicitStartingCursorSoFirstPollIgnoresOldRows() {
        insert(1, "Steve", 5, "node-B");
        insert(2, "Alex", 7, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncConfig config = RowSyncConfig.builder("players", "id", "row_version", "updated_by", "node-A")
                .startCursor(5)
                .build();
        RowSyncPoller<String> poller = poller(config, row -> applied.add(row.value()));

        poller.pollOnce();

        // Version 5 is at-or-below the start cursor, so only the strictly newer row 7 is applied.
        assertThat(applied).containsExactly("Alex");
    }

    @Test
    void skipsRowsWhoseLocalCopyIsDirty() {
        insert(1, "Steve", 5, "node-B");
        insert(2, "Alex", 7, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncPoller<String> poller = poller(config(), row -> applied.add(row.value()));
        // Pretend id 1 has an unsaved local edit; the peer's version must not clobber it. The key arrives as
        // whatever the driver maps the INTEGER column to (an Integer on SQLite), so match on its string form.
        poller.skipWhenDirty(key -> key.toString().equals("1"));

        poller.pollOnce();

        assertThat(applied).containsExactly("Alex");
    }

    @Test
    void dirtyRowStillAdvancesTheCursorSoItIsNotRetriedForever() {
        insert(1, "Steve", 5, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncPoller<String> poller = poller(config(), row -> applied.add(row.value()));
        poller.skipWhenDirty(key -> true);

        poller.pollOnce();

        // Nothing applied, but the cursor moved past the skipped row so the next poll does not re-fetch it.
        assertThat(applied).isEmpty();
        assertThat(poller.cursor()).isEqualTo(5);
        assertThat(poller.pollOnce()).isZero();
    }

    @Test
    void boundedBatchLimitsRowsPerPollAndTheRestFollowOnTheNextPoll() {
        for (int i = 1; i <= 5; i++) {
            insert(i, "p" + i, i, "node-B");
        }

        List<String> applied = new ArrayList<>();
        RowSyncConfig config = RowSyncConfig.builder("players", "id", "row_version", "updated_by", "node-A")
                .batchLimit(2)
                .build();
        RowSyncPoller<String> poller = poller(config, row -> applied.add(row.value()));

        assertThat(poller.pollOnce()).isEqualTo(2);
        assertThat(poller.pollOnce()).isEqualTo(2);
        assertThat(poller.pollOnce()).isEqualTo(1);
        assertThat(poller.pollOnce()).isZero();
        assertThat(applied).containsExactly("p1", "p2", "p3", "p4", "p5");
    }

    @Test
    void aFailingListenerOnOneRowDoesNotAbortTheRest() {
        insert(1, "bad", 5, "node-B");
        insert(2, "good", 7, "node-B");

        List<String> applied = new ArrayList<>();
        RowSyncPoller<String> poller = poller(config(), row -> {
            if (row.value().equals("bad")) {
                throw new IllegalStateException("boom");
            }
            applied.add(row.value());
        });

        poller.pollOnce();

        assertThat(applied).containsExactly("good");
        // The whole batch's cursor still advances; a poisoned row never wedges the poller.
        assertThat(poller.cursor()).isEqualTo(7);
    }

    @Test
    @SuppressWarnings("NullAway")
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new RowSyncPoller<>(null, config(), row -> "x", row -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RowSyncPoller<>(sql, null, row -> "x", row -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RowSyncPoller<>(sql, config(), null, row -> {}))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RowSyncPoller<>(sql, config(), row -> "x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void skipWhenDirtyRejectsNull() {
        RowSyncPoller<String> poller = poller(config(), row -> {});
        assertThatThrownBy(() -> poller.skipWhenDirty(null)).isInstanceOf(NullPointerException.class);
    }
}
