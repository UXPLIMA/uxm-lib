package com.uxplima.uxmlib.storage.migration;

import java.util.Objects;

/**
 * One schema migration: a monotonically increasing {@code version}, a human {@code description}, and the
 * {@code sql} to apply (one or more statements separated by {@code ;}). {@link MigrationRunner} applies
 * pending migrations in version order and records which have run, so each runs exactly once.
 */
public record Migration(int version, String description, String sql) {

    /** The mark {@link #withTablePrefix(String)} replaces. */
    public static final String PREFIX_MARK = "{prefix}";

    public Migration {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(sql, "sql");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    /**
     * This migration with every {@code {prefix}} in its SQL replaced by {@code tablePrefix}, so one file
     * builds the schema under whatever prefix an operator chose.
     */
    public Migration withTablePrefix(String tablePrefix) {
        Objects.requireNonNull(tablePrefix, "tablePrefix");
        return new Migration(version, description, sql.replace(PREFIX_MARK, tablePrefix));
    }
}
