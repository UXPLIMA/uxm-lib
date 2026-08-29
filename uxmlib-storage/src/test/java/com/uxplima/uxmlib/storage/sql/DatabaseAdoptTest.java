package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

/**
 * {@link Database#adopt} wraps a DataSource the caller owns: queries run on it, but the adopted handle's
 * {@link Database#close()} must not shut the pool down: the owner stays in charge of its lifecycle.
 */
class DatabaseAdoptTest {

    @Test
    void runsQueriesAgainstAnAdoptedDataSource() {
        try (HikariDataSource external = hikari("jdbc:sqlite:file:adopt1?mode=memory&cache=shared")) {
            Database database = Database.adopt(external, Dialect.SQLITE);
            assertThat(database.dialect()).isEqualTo(Dialect.SQLITE);
            Sql sql = new Sql(database);
            sql.execute("CREATE TABLE t (id INTEGER PRIMARY KEY)");
            assertThat(sql.update("INSERT INTO t (id) VALUES (?)", ps -> ps.setInt(1, 1)))
                    .isEqualTo(1);
        }
    }

    @Test
    void closeDoesNotShutDownAnAdoptedPool() {
        try (HikariDataSource external = hikari("jdbc:sqlite:file:adopt2?mode=memory&cache=shared")) {
            Database database = Database.adopt(external, Dialect.SQLITE);
            database.close();
            assertThat(external.isClosed()).isFalse();
            assertThat(new Sql(database).query("SELECT 1", StatementBinder.NONE, row -> row.getInt(1)))
                    .containsExactly(1);
        }
    }

    private static HikariDataSource hikari(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }
}
