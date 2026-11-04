package com.uxplima.uxmlib.bedrock;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Whether a Bedrock player chose chest menus over native forms, kept on the player's own data so every plugin built
 * on this library reads the same answer.
 *
 * <p>Each plugin shades this library under its own package, so a service registered under one of its classes is a
 * different class to every other plugin. The player's data is the one place they all reach, which is why the choice
 * lives there under the plain key {@code uxmlib:bedrock_menus}, the same way {@code SharedLanguage} keeps the
 * language. The plugin that offers the choice writes it, keeps it in its own storage and writes it again when the
 * player joins; a menu reads it when it opens.
 *
 * <p>A player's data may only be touched on the thread that owns the player. A read anywhere else answers "forms",
 * the default, rather than reaching across threads.
 */
public final class BedrockMenuChoice {

    /** Where the choice is kept. A plain key, so that every copy of this library finds the same one. */
    public static final NamespacedKey KEY = new NamespacedKey("uxmlib", "bedrock_menus");

    private static final String CHEST = "chest";

    private static final String FORM = "form";

    private BedrockMenuChoice() {}

    /** Whether {@code player} asked for chest menus instead of forms. False when nothing is stored. */
    public static boolean wantsChest(Player player) {
        Objects.requireNonNull(player, "player");
        if (!ownedHere(player)) {
            return false;
        }
        return CHEST.equals(player.getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    /**
     * Store {@code player}'s choice. Must run on the thread that owns the player.
     *
     * @param chest {@code true} for chest menus, {@code false} for forms
     */
    public static void write(Player player, boolean chest) {
        Objects.requireNonNull(player, "player");
        player.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, chest ? CHEST : FORM);
    }

    private static boolean ownedHere(Player player) {
        try {
            return Bukkit.isOwnedByCurrentRegion(player);
        } catch (RuntimeException | LinkageError noServer) {
            return false;
        }
    }
}
