package com.uxplima.uxmlib.storage.sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.uxplima.uxmlib.storage.StorageException;

/**
 * Carries a schema's rows from one database into another: every table under a prefix, row for row, into a target
 * whose tables were created by the same migrations and hold nothing yet.
 *
 * <p>It is how a store that kept its rows in a SQLite file moves them to the network database its block now names.
 * The copy fills empty tables only, so it never merges two sets of rows or overwrites one. The migration history
 * and the table of notices between servers stay behind: the target keeps its own.
 *
 * <p>The whole copy is one transaction on the target. A failure halfway leaves the target as empty as it was.
 */
public final class StorageCopy {

    private static final System.Logger LOG = System.getLogger(StorageCopy.class.getName());
    private static final int BATCH = 500;

    private StorageCopy() {}

    /**
     * Copy the rows of the SQLite file {@code settings} names into {@code target}, which is where {@code settings}
     * points. The file is opened on its own pool, brought up to date by {@code migrate}, copied and closed.
     *
     * @param migrate runs the schema's migrations on the database it is handed
     * @throws Refused when the block names a SQLite file itself, when no file is there, or for any refusal of
     *     {@link #copy}
     */
    public static Copied fromFile(
            StorageSettings settings, Path dataFolder, Consumer<Database> migrate, Database target) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(migrate, "migrate");
        Objects.requireNonNull(target, "target");
        if (settings.backend().isFile()) {
            throw new Refused(
                    Reason.ALREADY_ON_FILE, settings.file(), "the store already keeps its rows in " + settings.file());
        }
        StorageSettings file = settings.localFile();
        // Opening a SQLite pool creates the file, and an empty one left behind would read as rows that vanished.
        if (!Files.isRegularFile(dataFolder.resolve(file.file()))) {
            throw new Refused(Reason.NO_FILE, file.file(), "there is no " + file.file() + " to copy from");
        }
        try (Database source = file.open(dataFolder, "copy-" + file.file())) {
            migrate.accept(source);
            return copy(source, target, settings.tablePrefix());
        }
    }

    /**
     * At start, after {@code target} is migrated: when the block sets {@code copy-from-file}, copy the file's rows
     * into it with {@link #fromFile} and say in the log what happened.
     *
     * <p>A refusal is told in the log and the start goes on: the target is as it was, and the most common one is a
     * later start finding the rows already there.
     *
     * @return what was copied; empty when the block did not ask or the copy was refused
     * @throws StorageException when the databases fail; the target is rolled back
     */
    public static Optional<Copied> atStart(
            StorageSettings settings, Path dataFolder, Consumer<Database> migrate, Database target) {
        Objects.requireNonNull(settings, "settings");
        if (!settings.copyFromFile()) {
            return Optional.empty();
        }
        try {
            Copied copied = fromFile(settings, dataFolder, migrate, target);
            LOG.log(
                    System.Logger.Level.INFO,
                    "Copied " + copied.total() + " rows from " + settings.file() + " under '" + settings.tablePrefix()
                            + "': " + copied.rows() + ". Set copy-from-file = false.");
            return Optional.of(copied);
        } catch (Refused refused) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "copy-from-file copied nothing from " + settings.file() + ": " + refused.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Copy every row of every table under {@code tablePrefix} from {@code from} into {@code to}.
     *
     * @return the rows copied, by table, in the order the tables were filled
     * @throws Refused when a table is not in the target, a column is not in the target table, or a target table
     *     already holds rows; nothing is written then
     * @throws StorageException when the databases fail; the target is rolled back
     */
    public static Copied copy(Database from, Database to, String tablePrefix) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(tablePrefix, "tablePrefix");
        try (Connection source = from.connection();
                Connection target = to.connection()) {
            List<Table> tables = plan(source, target, tablePrefix);
            for (Table table : tables) {
                if (holdsRows(target, table.target())) {
                    throw new Refused(
                            Reason.TARGET_HOLDS_ROWS,
                            table.target(),
                            table.target() + " already holds rows in the target; the copy fills empty tables only");
                }
            }
            return fill(source, target, inParentOrder(target, tables), to.dialect() == Dialect.POSTGRES);
        } catch (SQLException failure) {
            throw new StorageException("could not copy the rows under '" + tablePrefix + "'", failure);
        }
    }

    private static Copied fill(Connection source, Connection target, List<Table> tables, boolean postgres)
            throws SQLException {
        boolean autoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try {
            Map<String, Integer> rows = new LinkedHashMap<>();
            for (Table table : tables) {
                rows.put(table.target(), fill(source, target, table, postgres));
                if (postgres) {
                    carryCounters(target, table);
                }
            }
            target.commit();
            return new Copied(rows);
        } catch (SQLException | RuntimeException failure) {
            target.rollback();
            throw failure;
        } finally {
            target.setAutoCommit(autoCommit);
        }
    }

    private static int fill(Connection source, Connection target, Table table, boolean postgres) throws SQLException {
        String read = "SELECT " + table.columns().stream().map(Column::source).collect(Collectors.joining(", "))
                + " FROM " + table.source();
        String write = "INSERT INTO " + table.target() + " ("
                + table.columns().stream().map(Column::target).collect(Collectors.joining(", ")) + ") VALUES ("
                + table.columns().stream().map(column -> "?").collect(Collectors.joining(", ")) + ")";
        int copied = 0;
        try (Statement select = source.createStatement();
                ResultSet rows = select.executeQuery(read);
                PreparedStatement insert = target.prepareStatement(write)) {
            while (rows.next()) {
                for (int index = 1; index <= table.columns().size(); index++) {
                    bind(
                            insert,
                            index,
                            rows.getObject(index),
                            table.columns().get(index - 1).type(),
                            postgres);
                }
                insert.addBatch();
                copied++;
                if (copied % BATCH == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
        return copied;
    }

    // MySQL, MariaDB and SQLite take a value of any kind into a column of another and convert it, the way they
    // would from a literal. PostgreSQL wants the type named, and will not read a number as a boolean at all,
    // which is how SQLite keeps one.
    private static void bind(PreparedStatement insert, int index, Object value, int type, boolean postgres)
            throws SQLException {
        if (value == null) {
            insert.setNull(index, type);
        } else if (!postgres) {
            insert.setObject(index, value);
        } else if (type == Types.BOOLEAN || type == Types.BIT) {
            insert.setBoolean(index, truth(value));
        } else {
            insert.setObject(index, value, type);
        }
    }

    private static boolean truth(Object value) {
        return switch (value) {
            case Boolean bool -> bool;
            case Number number -> number.doubleValue() != 0;
            case String text -> text.equals("1") || text.equalsIgnoreCase("true");
            default -> throw new IllegalArgumentException("not a truth value: " + value);
        };
    }

    // A column PostgreSQL numbers by itself keeps its own counter, which the copied rows did not move. Left alone
    // it would hand out 1 again, a key the copy already took.
    private static void carryCounters(Connection target, Table table) throws SQLException {
        for (Column column : table.columns()) {
            String sequence;
            try (PreparedStatement find = target.prepareStatement("SELECT pg_get_serial_sequence(?, ?)")) {
                find.setString(1, table.target());
                find.setString(2, column.target());
                try (ResultSet found = find.executeQuery()) {
                    sequence = found.next() ? found.getString(1) : null;
                }
            }
            if (sequence == null) {
                continue;
            }
            try (PreparedStatement carry =
                    target.prepareStatement("SELECT setval(CAST(? AS regclass), COALESCE((SELECT MAX(" + column.target()
                            + ") FROM " + table.target() + "), 0) + 1, false)")) {
                carry.setString(1, sequence);
                carry.executeQuery().close();
            }
        }
    }

    private static List<Table> plan(Connection source, Connection target, String tablePrefix) throws SQLException {
        Map<String, String> targetTables = tables(target, tablePrefix);
        List<Table> tables = new ArrayList<>();
        for (String sourceTable : tables(source, tablePrefix).values()) {
            String targetTable = targetTables.get(sourceTable.toLowerCase(Locale.ROOT));
            if (targetTable == null) {
                throw new Refused(
                        Reason.TABLE_MISSING,
                        sourceTable,
                        sourceTable + " is not in the target; migrate the target before copying into it");
            }
            Map<String, Column> targetColumns = new LinkedHashMap<>();
            for (Column column : columns(target, targetTable)) {
                targetColumns.put(column.target().toLowerCase(Locale.ROOT), column);
            }
            List<Column> columns = new ArrayList<>();
            for (Column column : columns(source, sourceTable)) {
                Column into = targetColumns.get(column.source().toLowerCase(Locale.ROOT));
                if (into == null) {
                    throw new Refused(
                            Reason.COLUMN_MISSING,
                            sourceTable,
                            sourceTable + "." + column.source()
                                    + " has no column in the target; its values would be lost");
                }
                columns.add(new Column(column.source(), into.target(), into.type()));
            }
            tables.add(new Table(sourceTable, targetTable, List.copyOf(columns)));
        }
        return tables;
    }

    // Every table under the prefix, by its name in lower case, in name order. The bookkeeping is left out: the
    // migration history, the notices between servers, and SQLite's own tables.
    private static Map<String, String> tables(Connection connection, String tablePrefix) throws SQLException {
        String prefix = tablePrefix.toLowerCase(Locale.ROOT);
        Map<String, String> tables = new TreeMap<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet found =
                meta.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE"})) {
            while (found.next()) {
                String name = found.getString("TABLE_NAME");
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith(prefix)
                        && !lower.endsWith("schema_history")
                        && !lower.equals(prefix + "sync")
                        && !lower.startsWith("sqlite_")) {
                    tables.put(lower, SqlIdentifiers.identifier(name, "table"));
                }
            }
        }
        return tables;
    }

    private static List<Column> columns(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<Column> columns = new ArrayList<>();
        try (ResultSet found =
                meta.getColumns(connection.getCatalog(), connection.getSchema(), escaped(meta, table), "%")) {
            while (found.next()) {
                // The name is a pattern, and an underscore in it matches any letter, so the rows are checked.
                if (found.getString("TABLE_NAME").equalsIgnoreCase(table)) {
                    String name = SqlIdentifiers.identifier(found.getString("COLUMN_NAME"), "column");
                    columns.add(new Column(name, name, found.getInt("DATA_TYPE")));
                }
            }
        }
        return columns;
    }

    private static String escaped(DatabaseMetaData meta, String name) throws SQLException {
        String escape = meta.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            return name;
        }
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static boolean holdsRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet found = statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
            return found.next();
        }
    }

    // A table another one points at is filled before it, so the target's foreign keys hold at every row. Tables
    // that point at each other in a ring keep their name order; a ring cannot be filled one table at a time.
    private static List<Table> inParentOrder(Connection target, List<Table> tables) throws SQLException {
        Map<String, Table> byName = new LinkedHashMap<>();
        for (Table table : tables) {
            byName.put(table.target().toLowerCase(Locale.ROOT), table);
        }
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        DatabaseMetaData meta = target.getMetaData();
        for (String name : byName.keySet()) {
            Set<String> mine = new HashSet<>();
            try (ResultSet keys = meta.getImportedKeys(
                    target.getCatalog(), target.getSchema(), byName.get(name).target())) {
                while (keys.next()) {
                    String parent = keys.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT);
                    if (!parent.equals(name) && byName.containsKey(parent)) {
                        mine.add(parent);
                    }
                }
            }
            parents.put(name, mine);
        }
        List<Table> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Map.Entry<String, Set<String>> table : parents.entrySet()) {
                if (!placed.contains(table.getKey()) && placed.containsAll(table.getValue())) {
                    placed.add(table.getKey());
                    ordered.add(byName.get(table.getKey()));
                    moved = true;
                }
            }
        }
        for (Map.Entry<String, Table> table : byName.entrySet()) {
            if (!placed.contains(table.getKey())) {
                ordered.add(table.getValue());
            }
        }
        return ordered;
    }

    private record Table(String source, String target, List<Column> columns) {}

    private record Column(String source, String target, int type) {}

    /**
     * What a copy carried.
     *
     * @param rows the rows copied, by table, in the order the tables were filled
     */
    public record Copied(Map<String, Integer> rows) {

        public Copied {
            rows = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rows, "rows")));
        }

        /** Every row copied, over all tables. */
        public int total() {
            return rows.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Why a copy did not start. */
    public enum Reason {
        /** The block names a SQLite file, so there is nowhere else for its rows to go. */
        ALREADY_ON_FILE,
        /** There is no file to copy from. */
        NO_FILE,
        /** A table in the source is not in the target. */
        TABLE_MISSING,
        /** A column in the source is not in the target table. */
        COLUMN_MISSING,
        /** A target table already holds rows. */
        TARGET_HOLDS_ROWS
    }

    /** A copy that did not start, and wrote nothing. */
    public static final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final Reason reason;
        private final String table;

        Refused(Reason reason, String table, String message) {
            super(message);
            this.reason = reason;
            this.table = table;
        }

        /** Why. */
        public Reason reason() {
            return reason;
        }

        /** The table the refusal is about, or the file for {@link Reason#ALREADY_ON_FILE} and {@link Reason#NO_FILE}. */
        public String table() {
            return table;
        }
    }
}
