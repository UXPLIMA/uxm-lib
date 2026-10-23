package com.uxplima.uxmlib.storage.sql;

import java.util.List;

/**
 * The drivers a server does not carry, as Maven coordinates for a plugin's library loader.
 *
 * <p>A Paper server ships SQLite and MySQL's own driver and nothing else, so a plugin that offers
 * {@link Backend#MARIADB} and {@link Backend#POSTGRESQL} has the loader fetch these before it starts. They are
 * fetched rather than shaded: each plugin would otherwise carry its own copy of both.
 */
public final class StorageDrivers {

    /** MariaDB's driver, for {@link Backend#MARIADB}. */
    public static final String MARIADB = "org.mariadb.jdbc:mariadb-java-client:3.5.3";

    /** PostgreSQL's driver, for {@link Backend#POSTGRESQL}. */
    public static final String POSTGRESQL = "org.postgresql:postgresql:42.7.4";

    private StorageDrivers() {}

    /** Every driver a plugin fetches to offer every {@link Backend}. */
    public static List<String> coordinates() {
        return List.of(MARIADB, POSTGRESQL);
    }
}
