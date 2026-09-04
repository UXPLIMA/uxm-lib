package com.uxplima.uxmlib.text.language;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A plugin's own language store, in one HOCON file in its own folder:
 *
 * <pre>
 * players {
 *   "0b1e...-uuid" { chosen = "tr", client = "en" }
 * }
 * </pre>
 *
 * <p>Every read answers from memory, because a read happens on the thread that draws a message. A write marks
 * the store dirty and nothing touches the disk until {@link #save} is called, so the caller decides when the
 * write happens: on a timer, when a player leaves, or when the plugin stops. {@link #dirty} says whether
 * there is anything to write.
 *
 * <p>An entry the file cannot parse is dropped and the rest of the file still loads. A store that refuses to
 * load would take the whole plugin down over one hand-edited line.
 */
public final class FilePlayerLanguages implements PlayerLanguages {

    private static final String PLAYERS = "players";

    private final Path file;

    private final Map<UUID, Locale> choices = new ConcurrentHashMap<>();

    private final Map<UUID, Locale> clients = new ConcurrentHashMap<>();

    private final AtomicBoolean dirty = new AtomicBoolean();

    private FilePlayerLanguages(Path file) {
        this.file = file;
    }

    /**
     * Read {@code file} now, and answer from memory afterwards.
     *
     * @throws UncheckedIOException when the file exists and cannot be read, which an operator has to see
     */
    public static FilePlayerLanguages loadedFrom(Path file) {
        Objects.requireNonNull(file, "file");
        FilePlayerLanguages store = new FilePlayerLanguages(file);
        store.load();
        return store;
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            CommentedConfigurationNode root =
                    HoconConfigurationLoader.builder().path(file).build().load();
            for (var entry : root.node(PLAYERS).childrenMap().entrySet()) {
                read(String.valueOf(entry.getKey()), entry.getValue());
            }
        } catch (ConfigurateException unreadable) {
            throw new UncheckedIOException("cannot read the language store " + file, unreadable);
        }
    }

    private void read(String key, CommentedConfigurationNode entry) {
        UUID player;
        try {
            player = UUID.fromString(key);
        } catch (IllegalArgumentException notAPlayer) {
            return;
        }
        put(choices, player, entry.node("chosen").getString());
        put(clients, player, entry.node("client").getString());
    }

    private static void put(Map<UUID, Locale> into, UUID player, @Nullable String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        Locale locale = Locale.forLanguageTag(tag);
        if (!locale.getLanguage().isEmpty()) {
            into.put(player, locale);
        }
    }

    /** Whether a write is owed. A store nobody changed writes nothing. */
    public boolean dirty() {
        return dirty.get();
    }

    /**
     * Write the whole store, through a temporary file and a move, so a crash never leaves half a file behind.
     * Does nothing when nothing changed.
     *
     * @throws UncheckedIOException when the write fails, because a lost choice has to be visible
     */
    public void save() {
        if (!dirty.compareAndSet(true, false)) {
            return;
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(temp.toAbsolutePath().getParent());
            HoconConfigurationLoader loader =
                    HoconConfigurationLoader.builder().path(temp).build();
            loader.save(tree(loader));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            dirty.set(true);
            throw new UncheckedIOException("cannot write the language store " + file, failure);
        }
    }

    private CommentedConfigurationNode tree(HoconConfigurationLoader loader) throws ConfigurateException {
        CommentedConfigurationNode root = loader.createNode();
        for (UUID player : union()) {
            CommentedConfigurationNode entry = root.node(PLAYERS, player.toString());
            write(entry.node("chosen"), choices.get(player));
            write(entry.node("client"), clients.get(player));
        }
        return root;
    }

    private Set<UUID> union() {
        Set<UUID> players = new LinkedHashSet<>(choices.keySet());
        players.addAll(clients.keySet());
        return players;
    }

    private static void write(CommentedConfigurationNode node, @Nullable Locale locale) throws ConfigurateException {
        if (locale != null) {
            node.set(locale.toLanguageTag());
        }
    }

    @Override
    public Optional<Locale> chosen(UUID player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(choices.get(player));
    }

    @Override
    public void choose(UUID player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        choices.put(player, locale);
        dirty.set(true);
    }

    @Override
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        if (choices.remove(player) != null) {
            dirty.set(true);
        }
    }

    @Override
    public Optional<Locale> lastClient(UUID player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(clients.get(player));
    }

    @Override
    public void rememberClient(UUID player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        Locale previous = clients.put(player, locale);
        if (!locale.equals(previous)) {
            dirty.set(true);
        }
    }
}
