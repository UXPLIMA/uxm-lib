package com.uxplima.uxmlib.storage.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A small, safe SELECT builder. Every <em>value</em> goes through a bound {@code ?} placeholder, and the
 * column/table identifiers and comparison operators it writes into the SQL are validated against a strict
 * allowlist, so the produced {@link Query} is injection-safe by construction even if a caller threads
 * untrusted input into a column name. Deliberately minimal, for joins or vendor-specific SQL, write the
 * statement directly and use {@link Sql#query(String, StatementBinder, RowMapper)}.
 *
 * <pre>{@code
 * Query q = SelectBuilder.from("players")
 *     .columns("name", "coins")
 *     .where("world", "world_nether")
 *     .orderByDescending("coins")
 *     .limit(10)
 *     .build();
 * }</pre>
 */
public final class SelectBuilder {

    // A SQL identifier we are willing to inline: a bare name or dotted/quoted-free table.column, nothing
    // that could carry an injection. Anything else must go through a hand-written statement instead.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    // The only comparison operators the builder will inline; everything else is rejected.
    private static final Set<String> OPERATORS = Set.of("=", "!=", "<>", "<", "<=", ">", ">=", "LIKE", "IS", "IS NOT");

    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();
    private @org.jspecify.annotations.Nullable String orderBy;
    private boolean descending;
    private int limit = -1;
    private int offset = -1;

    private SelectBuilder(String table) {
        this.table = identifier(table, "table");
    }

    /** Start a SELECT from {@code table}. */
    public static SelectBuilder from(String table) {
        return new SelectBuilder(table);
    }

    /** The columns to select; selects {@code *} when none are given. */
    public SelectBuilder columns(String... names) {
        Objects.requireNonNull(names, "names");
        for (String name : names) {
            columns.add(identifier(name, "column"));
        }
        return this;
    }

    /** An equality condition {@code column = ?} bound to {@code value}. Conditions are AND-combined. */
    public SelectBuilder where(String column, Object value) {
        Objects.requireNonNull(value, "value");
        conditions.add(identifier(column, "column") + " = ?");
        parameters.add(value);
        return this;
    }

    /** A comparison condition {@code column <op> ?} bound to {@code value} (op e.g. {@code ">="}). */
    public SelectBuilder where(String column, String operator, Object value) {
        Objects.requireNonNull(value, "value");
        conditions.add(identifier(column, "column") + " " + operator(operator) + " ?");
        parameters.add(value);
        return this;
    }

    /** A membership condition {@code column IN (?, ?, …)}, one bound placeholder per value (at least one). */
    public SelectBuilder whereIn(String column, Object... values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("whereIn needs at least one value");
        }
        String placeholders =
                java.util.stream.Stream.generate(() -> "?").limit(values.length).collect(Collectors.joining(", "));
        conditions.add(identifier(column, "column") + " IN (" + placeholders + ")");
        for (Object value : values) {
            parameters.add(Objects.requireNonNull(value, "value"));
        }
        return this;
    }

    /**
     * An OR-combined group of equality conditions, wrapped in parentheses and AND-joined with the rest, e.g.
     * {@code (name = ? OR name = ?)}. The group must contain at least one condition.
     */
    public SelectBuilder whereAny(Consumer<OrGroup> build) {
        Objects.requireNonNull(build, "build");
        OrGroup group = new OrGroup();
        build.accept(group);
        if (group.fragments.isEmpty()) {
            throw new IllegalArgumentException("whereAny needs at least one condition");
        }
        conditions.add("(" + String.join(" OR ", group.fragments) + ")");
        parameters.addAll(group.values);
        return this;
    }

    /**
     * A case-insensitive pattern match {@code LOWER(column) LIKE LOWER(?)} bound to {@code pattern}. Lowering
     * both sides is portable across SQLite, H2, MySQL and Postgres without relying on a collation.
     */
    public SelectBuilder whereLikeIgnoreCase(String column, String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        conditions.add("LOWER(" + identifier(column, "column") + ") LIKE LOWER(?)");
        parameters.add(pattern);
        return this;
    }

    /** Order ascending by {@code column}. */
    public SelectBuilder orderBy(String column) {
        this.orderBy = identifier(column, "column");
        this.descending = false;
        return this;
    }

    /** Order descending by {@code column}. */
    public SelectBuilder orderByDescending(String column) {
        this.orderBy = identifier(column, "column");
        this.descending = true;
        return this;
    }

    /** Limit the result to {@code max} rows. */
    public SelectBuilder limit(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.limit = max;
        return this;
    }

    /** Skip the first {@code rows} rows of the result; {@code 0} disables. */
    public SelectBuilder offset(int rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        this.offset = rows;
        return this;
    }

    /** Render the {@link Query}. */
    public Query build() {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns.isEmpty() ? "*" : String.join(", ", columns));
        sql.append(" FROM ").append(table);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (orderBy != null) {
            sql.append(" ORDER BY ").append(orderBy).append(descending ? " DESC" : " ASC");
        }
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        if (offset >= 0) {
            sql.append(" OFFSET ").append(offset);
        }
        return new Query(sql.toString(), parameters);
    }

    private static String identifier(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value
                    + "'); write the statement by hand for anything else");
        }
        return value;
    }

    private static String operator(String value) {
        Objects.requireNonNull(value, "operator");
        String normalised = value.strip().toUpperCase(Locale.ROOT);
        if (!OPERATORS.contains(normalised)) {
            throw new IllegalArgumentException("unsupported operator '" + value + "'; allowed: " + OPERATORS);
        }
        return normalised;
    }

    /**
     * Collects the equality conditions of a single OR-combined group for {@link #whereAny(Consumer)}. Each
     * {@link #eq} contributes a {@code column = ?} fragment OR-joined with the others; values are bound, never
     * inlined, so the group is injection-safe just like the rest of the builder.
     */
    public static final class OrGroup {

        private final List<String> fragments = new ArrayList<>();
        private final List<Object> values = new ArrayList<>();

        private OrGroup() {}

        /** Add an equality alternative {@code column = ?} bound to {@code value}. */
        public OrGroup eq(String column, Object value) {
            Objects.requireNonNull(value, "value");
            fragments.add(identifier(column, "column") + " = ?");
            values.add(value);
            return this;
        }
    }
}
