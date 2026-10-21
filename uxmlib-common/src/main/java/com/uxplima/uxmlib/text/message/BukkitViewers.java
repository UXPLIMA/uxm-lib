package com.uxplima.uxmlib.text.message;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

import org.jspecify.annotations.Nullable;

/**
 * What a Bukkit player can say about itself, asked only where Bukkit exists.
 *
 * <p>Messages and locale sources run on a proxy too, where there is no Bukkit: a check against {@link Player} there
 * throws {@code NoClassDefFoundError} the first time it runs. So the check is made once, on whether the type can be
 * loaded at all, and the type is named only inside {@link Present}, which is loaded only when it can be.
 */
final class BukkitViewers {

    private static final boolean PRESENT = present();

    private BukkitViewers() {}

    /** The name of {@code viewer} when it is a Bukkit player and has one. */
    static Optional<String> nameOf(Audience viewer) {
        return PRESENT ? Present.nameOf(viewer) : Optional.empty();
    }

    /** The client language of {@code viewer} when it is a Bukkit player and reports one. */
    static @Nullable Locale localeOf(Audience viewer) {
        return PRESENT ? Present.localeOf(viewer) : null;
    }

    private static boolean present() {
        try {
            Class.forName("org.bukkit.entity.Player", false, BukkitViewers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** The only place the Bukkit player type is named, so a server without it never loads this class. */
    private static final class Present {

        private Present() {}

        static Optional<String> nameOf(Audience viewer) {
            return viewer instanceof Player player ? Optional.ofNullable(player.getName()) : Optional.empty();
        }

        static @Nullable Locale localeOf(Audience viewer) {
            // Paper says never null, and a player still joining, or one a test mocks, answers it anyway.
            return viewer instanceof Player player ? player.locale() : null;
        }
    }
}
