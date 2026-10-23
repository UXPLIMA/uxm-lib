package com.uxplima.uxmlib.storage.sql;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The database a schema lives in, as an operator names it in a configuration file.
 *
 * <p>MySQL and MariaDB are separate names because they are separate drivers: a server carries MySQL's own
 * driver, and MariaDB's is fetched beside PostgreSQL's (see {@link StorageDrivers}). Both speak the
 * {@link Dialect#MYSQL} dialect.
 */
public enum Backend {
    SQLITE("sqlite", "org.sqlite.JDBC", 0),
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", 3306),
    MARIADB("mariadb", "org.mariadb.jdbc.Driver", 3306),
    POSTGRESQL("postgresql", "org.postgresql.Driver", 5432);

    private final String id;
    private final String driverClassName;
    private final int defaultPort;

    Backend(String id, String driverClassName, int defaultPort) {
        this.id = id;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
    }

    /**
     * The backend an operator named, in any case. {@code postgres} is read as {@link #POSTGRESQL}.
     *
     * @throws IllegalArgumentException for a name no backend answers to: a typo that quietly became a
     *     SQLite file would put a network's rows on one machine and nobody would notice until they moved
     */
    public static Backend named(String name) {
        Objects.requireNonNull(name, "name");
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        if (wanted.equals("postgres")) {
            return POSTGRESQL;
        }
        for (Backend backend : values()) {
            if (backend.id.equals(wanted)) {
                return backend;
            }
        }
        throw new IllegalArgumentException("unknown storage type '" + name + "', expected one of "
                + Arrays.stream(values()).map(Backend::id).collect(Collectors.joining(", ")));
    }

    /** The name an operator writes, and the JDBC subprotocol. */
    public String id() {
        return id;
    }

    /** The driver class the pool loads, so a driver on a plugin's own class path is found. */
    public String driverClassName() {
        return driverClassName;
    }

    /** The port the server listens on unless told otherwise; 0 for a file. */
    public int defaultPort() {
        return defaultPort;
    }

    /** Whether rows live in a file beside the plugin rather than on a server. */
    public boolean isFile() {
        return this == SQLITE;
    }
}
