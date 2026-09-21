package com.uxplima.uxmlib.common;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import org.jspecify.annotations.Nullable;

/**
 * Resolves a config string such as {@code "block.note_block.pling"} or {@code "minecraft:block.bell.use"}
 * to a native {@link Sound} via the sound registry. A bare key takes the {@code minecraft} namespace. Bad
 * input never throws: an unknown or malformed key yields {@link Optional#empty()}, so a typo in a config
 * cannot crash the caller.
 *
 * <p><strong>The constant spelling resolves too.</strong> {@code BLOCK_NOTE_BLOCK_PLING} is what a wiki
 * prints and what an operator copies, and it is the registry key with every dot turned into an underscore.
 * Until 2026-09-22 it resolved to nothing here, and nothing said so: thirty three shipped values across
 * this estate were written that way and every one of them was silence. The fold cannot be undone by
 * guessing which underscores were dots, so the registry is asked instead.
 */
public final class Sounds {

    /**
     * Every sound, by its name with the dots turned into underscores and the whole thing upper cased.
     *
     * <p>Built on the first miss rather than on class load, because the registry needs a server and this
     * class is reached from tests that have none. Rebuilt never: the sound registry does not change while
     * a server runs.
     */
    private static volatile @Nullable Map<String, Sound> byConstantName;

    private Sounds() {}

    /**
     * Resolve {@code key} to a {@link Sound}, or empty if it is malformed or names no registered sound. The
     * key is lower-cased first, because {@link NamespacedKey} rejects the upper-case forms admins often type.
     */
    public static Optional<Sound> resolve(String key) {
        Objects.requireNonNull(key, "key");
        String written = key.trim();
        NamespacedKey parsed = NamespacedKey.fromString(written.toLowerCase(Locale.ROOT));
        if (parsed != null) {
            @Nullable Sound sound = Registry.SOUNDS.get(parsed);
            if (sound != null) {
                return Optional.of(sound);
            }
        }
        return Optional.ofNullable(constants().get(constantOf(written)));
    }

    /**
     * The registry key of {@code sound}, which is what Adventure and the client take.
     *
     * <p>It exists so that a caller holding the constant spelling can turn it into the spelling every
     * other part of the stack understands, rather than each caller folding it a different way.
     */
    public static Optional<String> keyOf(String written) {
        Objects.requireNonNull(written, "written");
        return resolve(written).map(sound -> Registry.SOUNDS.getKey(sound)).map(NamespacedKey::toString);
    }

    /** The name a wiki prints: the registry key with every dot turned into an underscore, upper cased. */
    private static String constantOf(String written) {
        String name = written;
        int namespace = name.indexOf(':');
        if (namespace >= 0) {
            name = name.substring(namespace + 1);
        }
        return name.replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private static Map<String, Sound> constants() {
        Map<String, Sound> known = byConstantName;
        if (known != null) {
            return known;
        }
        Map<String, Sound> built = new HashMap<>();
        for (Sound sound : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key != null) {
                built.put(constantOf(key.getKey()), sound);
            }
        }
        byConstantName = built;
        return built;
    }
}
