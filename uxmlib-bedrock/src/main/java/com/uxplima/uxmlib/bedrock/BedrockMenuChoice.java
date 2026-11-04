package com.uxplima.uxmlib.bedrock;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Whether a Bedrock player chose chest menus over native forms, kept on the player's own data so every plugin built
 * on this library reads the same answer.
 *
 * <p>Each plugin shades this library under its own package, so a service registered under one of its classes is a
 * different class to every other plugin. The player's data is the one place they all reach, which is why the choice
 * lives there under the plain key {@code uxmlib:bedrock_menus}, as {@code SharedLanguage} keeps the language. The
 * plugin that offers the choice writes it, keeps it in its own storage and writes it again when the player joins; a
 * menu reads it when it opens.
 *
 * <p>The data may only be touched on the thread that owns the player. There it is read live and the answer is
 * remembered; anywhere else, a form callback for one, the remembered answer is used, and a write waits for the
 * owning thread. A player never read on their own thread counts as wanting forms, the default.
 */
public final class BedrockMenuChoice {

    /** Where the choice is kept. A plain key, so that every copy of this library finds the same one. */
    public static final NamespacedKey KEY = new NamespacedKey("uxmlib", "bedrock_menus");

    private static final String CHEST = "chest";

    private static final String FORM = "form";

    private final Predicate<Player> owned;

    private final BiConsumer<Player, Runnable> onOwner;

    /** The answer each player's data last gave, held while the player object lives. */
    private final Map<Player, Boolean> seen = Collections.synchronizedMap(new WeakHashMap<>());

    private BedrockMenuChoice(Predicate<Player> owned, BiConsumer<Player, Runnable> onOwner) {
        this.owned = owned;
        this.onOwner = onOwner;
    }

    /** The server's own rules: the region that owns a player, and that player's scheduler. */
    public static BedrockMenuChoice onServer() {
        return new BedrockMenuChoice(BedrockMenuChoice::ownedHere, BedrockMenuChoice::runForPlayer);
    }

    /**
     * The same, with the thread rules handed in.
     *
     * @param owned whether the calling thread owns a player
     * @param onOwner runs work on the thread that owns a player
     */
    public static BedrockMenuChoice with(Predicate<Player> owned, BiConsumer<Player, Runnable> onOwner) {
        return new BedrockMenuChoice(
                Objects.requireNonNull(owned, "owned"), Objects.requireNonNull(onOwner, "onOwner"));
    }

    /** Whether {@code player} asked for chest menus instead of forms. False when nothing is known. */
    public boolean wantsChest(Player player) {
        Objects.requireNonNull(player, "player");
        if (!owned.test(player)) {
            return Boolean.TRUE.equals(seen.get(player));
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        boolean chest = data != null && CHEST.equals(data.get(KEY, PersistentDataType.STRING));
        seen.put(player, chest);
        return chest;
    }

    /**
     * Store {@code player}'s choice, on the thread that owns the player.
     *
     * @param chest {@code true} for chest menus, {@code false} for forms
     */
    public void write(Player player, boolean chest) {
        Objects.requireNonNull(player, "player");
        seen.put(player, chest);
        Runnable work = () -> {
            PersistentDataContainer data = player.getPersistentDataContainer();
            if (data != null) {
                data.set(KEY, PersistentDataType.STRING, chest ? CHEST : FORM);
            }
        };
        if (owned.test(player)) {
            work.run();
        } else {
            onOwner.accept(player, work);
        }
    }

    private static boolean ownedHere(Player player) {
        try {
            Server server = Bukkit.getServer();
            return server != null && server.isOwnedByCurrentRegion(player);
        } catch (RuntimeException | LinkageError noServer) {
            return false;
        }
    }

    private static void runForPlayer(Player player, Runnable work) {
        JavaPlugin owner = JavaPlugin.getProvidingPlugin(BedrockMenuChoice.class);
        player.getScheduler().run(owner, task -> work.run(), null);
    }
}
