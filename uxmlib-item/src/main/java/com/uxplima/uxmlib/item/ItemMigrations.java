package com.uxplima.uxmlib.item;

import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Upgrades an item blob written by an older Minecraft version to the format the running server expects.
 *
 * <p>The mechanism is Paper's native DataFixerUpper: {@link ItemStack#deserializeBytes(byte[])} runs the
 * stored bytes through Mojang's data fixers, so reading an older blob on a newer server already yields a
 * correct, upgraded {@link ItemStack}. This helper layers two things on top of that read:
 *
 * <ul>
 *   <li>{@link #needsMigration(byte[])}: a pure check over the {@link ItemSerialization} header that says
 *       whether a blob predates the running server's data version (and so will be fixed up on read).
 *   <li>{@link #migrate(byte[])} / {@link #migrateItem(byte[])}: deserialize (Paper applies the fix) and
 *       re-stamp the result at the current data version, so the upgrade is persisted and the blob no longer
 *       reports as needing migration.
 * </ul>
 *
 * <p>Migrating an already-current blob is harmless: it round-trips to an equivalent item re-stamped at the
 * same version. The cross-version fixing itself (e.g. a 1.20 blob loaded on 1.21) cannot be reproduced in a
 * single-version unit test; it is exercised on a real upgraded server. The version-gap detection and the
 * re-stamp round-trip are covered by tests.
 */
public final class ItemMigrations {

    private ItemMigrations() {}

    /**
     * Whether {@code bytes} were written by an older Minecraft version than the running server and would be
     * upgraded on read. A header-bearing blob needs migration when its stamped data version is below the
     * server's; a legacy header-less blob (written before the stamp existed) predates the header era and is
     * treated as needing migration, since its true age is unknown.
     */
    public static boolean needsMigration(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        OptionalInt stamped = ItemSerialization.dataVersionOf(bytes);
        if (stamped.isEmpty()) {
            return true;
        }
        return stamped.getAsInt() < serverDataVersion();
    }

    /**
     * Read {@code bytes} (Paper's DataFixerUpper upgrades any older format on read) and re-serialize the
     * result with a fresh header stamped at the server's current data version. The returned blob no longer
     * reports as needing migration.
     *
     * @throws IllegalArgumentException if the bytes are not a valid serialized item
     */
    public static byte[] migrate(byte[] bytes) {
        return ItemSerialization.toBytes(migrateItem(bytes));
    }

    /**
     * Read {@code bytes} into an upgraded {@link ItemStack}. Paper's DataFixerUpper is applied during the
     * read, so an older blob yields an item valid for the running server.
     *
     * @throws IllegalArgumentException if the bytes are not a valid serialized item
     */
    public static ItemStack migrateItem(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return ItemSerialization.fromBytes(bytes);
    }

    @SuppressWarnings("deprecation") // getUnsafe() is the only API exposing the server's data version
    private static int serverDataVersion() {
        return Bukkit.getUnsafe().getDataVersion();
    }
}
