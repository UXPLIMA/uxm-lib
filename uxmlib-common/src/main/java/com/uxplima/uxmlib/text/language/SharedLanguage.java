package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.jspecify.annotations.Nullable;

/**
 * The language a player chose, kept on the player's own data so that every plugin of ours on the server reads
 * the same answer.
 *
 * <p>Each plugin shades this library under its own package. Two plugins therefore never share a class, and a
 * service one of them registers under a class of this library is a different class to the other. The player's
 * data is the one place every plugin reaches, so the choice is written there, under the plain key
 * {@code uxmlib:language} and as a plain language tag. An empty tag is a choice that was cleared: it stops a
 * choice that one plugin kept on its own from being shared again after a reset in another.
 *
 * <p>The data may only be touched on the thread that owns the player. There it is read live, and the answer is
 * remembered. Anywhere else the remembered answer is used, and a write waits for the owning thread. A plugin
 * that reads off that thread before any read on it falls back to its own store, which is the answer it gave
 * before this existed.
 *
 * <p>{@link LanguageResolver} does all of this for a plugin. A plugin that resolves a language its own way
 * reads and writes the choice through {@link #read} and {@link #write}, so it agrees with every other.
 *
 * <p>The data belongs to one server. A network carries the choice between servers through a provider such as
 * uxmLang, which writes it here when the player arrives.
 */
public final class SharedLanguage {

    /** Where the choice is kept. A plain key, so that every copy of this library finds the same one. */
    public static final NamespacedKey KEY = new NamespacedKey("uxmlib", "language");

    private static final String CLEARED = "";

    private final Predicate<Player> owned;

    private final BiConsumer<Player, Runnable> onOwner;

    private final Map<UUID, Mark> seen = new ConcurrentHashMap<>();

    /** What the player's data holds: a language, or {@code null} for a choice that was cleared. */
    public record Mark(@Nullable Locale locale) {}

    private SharedLanguage(Predicate<Player> owned, BiConsumer<Player, Runnable> onOwner) {
        this.owned = owned;
        this.onOwner = onOwner;
    }

    /** The server's own rules: the region that owns a player, and that player's scheduler. */
    public static SharedLanguage onServer() {
        return new SharedLanguage(SharedLanguage::ownedHere, SharedLanguage::runForPlayer);
    }

    /**
     * The same, with the thread rules handed in.
     *
     * @param owned whether the calling thread owns a player
     * @param onOwner runs work on the thread that owns a player
     */
    public static SharedLanguage with(Predicate<Player> owned, BiConsumer<Player, Runnable> onOwner) {
        return new SharedLanguage(Objects.requireNonNull(owned, "owned"), Objects.requireNonNull(onOwner, "onOwner"));
    }

    /** Whether the calling thread may touch this player's data. */
    public boolean owns(Player player) {
        return owned.test(player);
    }

    /** What the player's data says, or empty when nothing was ever written there. */
    public Optional<Mark> read(Player player) {
        UUID id = player.getUniqueId();
        if (!owned.test(player)) {
            return Optional.ofNullable(seen.get(id));
        }
        Optional<Mark> mark = markOn(player);
        mark.ifPresentOrElse(value -> seen.put(id, value), () -> seen.remove(id));
        return mark;
    }

    /** Write a choice, or clear it with {@code null}. */
    public void write(Player player, @Nullable Locale locale) {
        seen.put(player.getUniqueId(), new Mark(locale));
        String tag = locale == null ? CLEARED : locale.toLanguageTag();
        Runnable work = () -> {
            PersistentDataContainer data = player.getPersistentDataContainer();
            if (data != null) {
                data.set(KEY, PersistentDataType.STRING, tag);
            }
        };
        if (owned.test(player)) {
            work.run();
        } else {
            onOwner.accept(player, work);
        }
    }

    private static Optional<Mark> markOn(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data == null) {
            return Optional.empty();
        }
        String tag = data.get(KEY, PersistentDataType.STRING);
        if (tag == null) {
            return Optional.empty();
        }
        if (tag.isBlank()) {
            return Optional.of(new Mark(null));
        }
        Locale locale = Locale.forLanguageTag(tag.strip());
        return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(new Mark(locale));
    }

    private static boolean ownedHere(Player player) {
        Server server = Bukkit.getServer();
        return server != null && server.isOwnedByCurrentRegion(player);
    }

    private static void runForPlayer(Player player, Runnable work) {
        JavaPlugin owner = JavaPlugin.getProvidingPlugin(SharedLanguage.class);
        player.getScheduler().run(owner, task -> work.run(), null);
    }
}
