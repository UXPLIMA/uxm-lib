package com.uxplima.uxmlib.storage.sql;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.config.HoconConfig;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.migration.Migration;
import com.uxplima.uxmlib.storage.migration.MigrationRunner;
import com.uxplima.uxmlib.storage.migration.SchemaIntrospector;

/**
 * Where a schema keeps its rows, read from one block of a configuration file.
 *
 * <p>The block names its backend rather than spelling a JDBC URL, so an operator moving to a network database
 * changes a word and an address and never learns a driver's URL grammar:
 *
 * <pre>
 * storage {
 *   type = sqlite            # sqlite, mysql, mariadb or postgresql
 *   file = "rows.db"         # sqlite only, inside the data folder
 *   host = localhost
 *   port = 0                 # 0 is the backend's own: 3306, or 5432 for postgresql
 *   database = minecraft
 *   username = minecraft
 *   password = ""
 *   table-prefix = "rows_"
 *   pool-size = 4
 *   local-only = false       # true keeps this one server on its sqlite file
 *   copy-from-file = false   # true copies the file's rows into an empty network database at start
 * }
 * </pre>
 *
 * <p>{@code local-only} is for a network whose servers all carry the same block: the one server that must keep
 * rows of its own sets it, and nothing else in its file differs from the others. It then reads the block as a
 * SQLite file under the same name and prefix.
 *
 * <p>{@code copy-from-file} is for the move from the file to a network database: the start after the type changes
 * carries the file's rows across with {@link StorageCopy#atStart}, and fills empty tables only, so leaving it on
 * copies nothing twice.
 *
 * <p>Every key may be left out. The table prefix is written into every table name and into the history of the
 * migrations, so two schemas, or two copies of one under different prefixes, can share a database.
 *
 * @param file the SQLite file, relative to the data folder
 * @param tablePrefix a bare identifier, possibly empty
 */
public record StorageSettings(
        Backend backend,
        String file,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        int poolSize,
        boolean copyFromFile) {

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]*");
    private static final String DEFAULT_DATABASE = "minecraft";
    private static final int DEFAULT_POOL_SIZE = 4;

    public StorageSettings {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(tablePrefix, "tablePrefix");
        if (!PREFIX.matcher(tablePrefix).matches()) {
            throw new IllegalArgumentException(
                    "table-prefix may hold only letters, digits and underscores, got '" + tablePrefix + "'");
        }
        if (backend.isFile() && file.isBlank()) {
            throw new IllegalArgumentException("a sqlite store needs a file name");
        }
        if (!backend.isFile() && (port < 1 || port > 65_535)) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("pool-size must be at least 1, got " + poolSize);
        }
    }

    /**
     * Read the block at {@code path}.
     *
     * @param defaultFile the SQLite file a plugin ships with, inside its data folder
     * @param defaultPrefix the table prefix a plugin ships with
     */
    public static StorageSettings read(HoconConfig config, String path, String defaultFile, String defaultPrefix) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(defaultFile, "defaultFile");
        Objects.requireNonNull(defaultPrefix, "defaultPrefix");
        Backend backend = Backend.named(config.getString(path + ".type", Backend.SQLITE.id()));
        // A shipped file cannot know which server an operator will name, so 0 stands for that server's own port
        // and changing the type alone is enough.
        int port = config.getInt(path + ".port", 0);
        StorageSettings named = new StorageSettings(
                backend,
                config.getString(path + ".file", defaultFile),
                config.getString(path + ".host", "localhost"),
                port == 0 ? backend.defaultPort() : port,
                config.getString(path + ".database", DEFAULT_DATABASE),
                config.getString(path + ".username", DEFAULT_DATABASE),
                config.getString(path + ".password", ""),
                config.getString(path + ".table-prefix", defaultPrefix),
                config.getInt(path + ".pool-size", DEFAULT_POOL_SIZE),
                config.getBoolean(path + ".copy-from-file", false));
        return config.getBoolean(path + ".local-only", false) ? named.localFile() : named;
    }

    /**
     * The SQLite file this block names, under the same prefix: where its rows were kept before it named a network
     * database, and where a {@code local-only} server keeps them. A file is never asked to copy into itself.
     */
    public StorageSettings localFile() {
        // A file has one writer, so a pool of more connections would only wait on each other.
        return new StorageSettings(
                Backend.SQLITE, file, "localhost", 0, DEFAULT_DATABASE, DEFAULT_DATABASE, "", tablePrefix, 1, false);
    }

    /** The URL the driver reads. A file is resolved against {@code dataFolder}. */
    public String jdbcUrl(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        if (backend.isFile()) {
            return "jdbc:sqlite:" + dataFolder.resolve(file);
        }
        return "jdbc:" + backend.id() + "://" + host + ":" + port + "/" + database;
    }

    /** A table's full name under this prefix. */
    public String table(String name) {
        Objects.requireNonNull(name, "name");
        return tablePrefix + name;
    }

    /** The table the migrations record what they ran in, one for each prefix. */
    public String historyTable() {
        return table("schema_history");
    }

    /** Open the pool. A SQLite file is created in {@code dataFolder} when it is not there yet. */
    public Database open(Path dataFolder, String poolName) {
        Objects.requireNonNull(poolName, "poolName");
        DatabaseBuilder builder = Database.builder().poolName(poolName).driverClassName(backend.driverClassName());
        if (backend.isFile()) {
            return builder.sqlite(dataFolder.resolve(file)).build();
        }
        return builder.jdbcUrl(jdbcUrl(dataFolder))
                .username(username)
                .password(password)
                .maxPoolSize(poolSize)
                .build();
    }

    /** These settings under another table prefix. */
    public StorageSettings withTablePrefix(String prefix) {
        return new StorageSettings(
                backend, file, host, port, database, username, password, prefix, poolSize, copyFromFile);
    }

    /** These settings, asking or not asking for the file's rows at start. */
    public StorageSettings withCopyFromFile(boolean copy) {
        return new StorageSettings(
                backend, file, host, port, database, username, password, tablePrefix, poolSize, copy);
    }

    /**
     * Bring the schema in {@code database} up to date: every migration with {@code {prefix}} written in, recorded
     * in {@link #historyTable()}, after carrying over what the shared default history kept for it.
     *
     * @param firstTable a table, without the prefix, that only this schema creates; it is how the shared history
     *     is known to be this schema's (see {@link MigrationRunner#carryOver})
     * @return how many migrations ran
     */
    public int migrate(Database database, List<Migration> migrations, String firstTable) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(migrations, "migrations");
        Objects.requireNonNull(firstTable, "firstTable");
        MigrationRunner runner = new MigrationRunner(database, historyTable());
        runner.carryOver(MigrationRunner.DEFAULT_HISTORY_TABLE, table(firstTable));
        return runner.apply(migrations.stream()
                .map(migration -> migration.withTablePrefix(tablePrefix))
                .toList());
    }

    /**
     * What a health check says of this store: the backend, where it is, the prefix and the version its migrations
     * reached, for example {@code postgresql at db:5432/minecraft, prefix rows_, schema v4}. It only reads: a store
     * nothing has migrated says so and is given no history table for the asking. The password is never in it.
     */
    public String describe(Database opened) {
        Objects.requireNonNull(opened, "opened");
        String where = backend.isFile()
                ? backend.id() + " file " + file
                : backend.id() + " at " + host + ":" + port + "/" + database;
        return where + ", " + (tablePrefix.isEmpty() ? "no prefix" : "prefix " + tablePrefix) + ", " + schema(opened);
    }

    private String schema(Database opened) {
        String history = SqlIdentifiers.identifier(historyTable(), "history table");
        try {
            if (!new SchemaIntrospector(opened).tableExists(history)) {
                return "no schema recorded";
            }
            return new Sql(opened)
                    .queryFirst("SELECT MAX(version) FROM " + history, StatementBinder.NONE, row -> row.getInt(1))
                    .filter(version -> version > 0)
                    .map(version -> "schema v" + version)
                    .orElse("no schema recorded");
        } catch (StorageException unreadable) {
            return "schema unreadable";
        }
    }

    /** The password stays out of a log line or a dump. */
    @Override
    public String toString() {
        return "StorageSettings[" + backend.id() + ", " + (backend.isFile() ? file : host + ":" + port + "/" + database)
                + ", prefix '" + tablePrefix + "', pool " + poolSize + (copyFromFile ? ", copy from file" : "") + "]";
    }
}
