/**
 * Plain-JDBC persistence plumbing (no jOOQ, no Flyway, no Paper dependency) grouped into sub-packages:
 * {@code sql} (the HikariCP-pooled {@code Database}, {@code Sql}/{@code TxSql}, and the injection-safe
 * {@code SelectBuilder}), {@code migration} (versioned schema migration), and {@code repository}
 * (CRUD-by-id, storage-provider abstraction, write-through cache). This root holds only the shared
 * {@link com.uxplima.uxmlib.storage.StorageException}.
 */
@NullMarked
package com.uxplima.uxmlib.storage;

import org.jspecify.annotations.NullMarked;
