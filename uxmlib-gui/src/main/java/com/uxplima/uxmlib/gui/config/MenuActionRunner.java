package com.uxplima.uxmlib.gui.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.bukkit.Registry;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Runs what a menu file wrote, for one viewer.
 *
 * <p>The five built-in verbs are performed here so that sixteen plugins do not each write them again. A verb
 * a plugin registered is looked up in {@link MenuActions} and given the rest of the line.
 *
 * <p>A line may hold a token, such as {@code command:shop sell %item%}. The runner does not know what a token
 * means, so it passes every argument through the resolver it was built with. A plugin gives one that knows
 * its own tokens; the default resolver changes nothing.
 *
 * <p>Every verb runs on the thread that called, which is the thread of the click. Nothing here reaches for a
 * scheduler, so the runner is safe on Folia as long as the caller is.
 */
public final class MenuActionRunner {

    /** Opens another menu of the same plugin, by the name it is filed under. */
    @FunctionalInterface
    public interface Opener {
        void open(Player viewer, String menu);
    }

    private final MenuActions actions;
    private final Opener opener;
    private final UnaryOperator<String> resolve;

    /** A runner that passes every argument through unchanged. */
    public MenuActionRunner(MenuActions actions, Opener opener) {
        this(actions, opener, UnaryOperator.identity());
    }

    public MenuActionRunner(MenuActions actions, Opener opener, UnaryOperator<String> resolve) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.resolve = Objects.requireNonNull(resolve, "resolve");
    }

    /** Run every line, in the order the file wrote them. */
    public void run(Player viewer, List<MenuAction> lines) {
        Objects.requireNonNull(lines, "lines");
        for (MenuAction line : lines) {
            run(viewer, line);
        }
    }

    /** Run one line. */
    public void run(Player viewer, MenuAction action) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(action, "action");
        switch (action) {
            case MenuAction.Close ignored -> viewer.closeInventory();
            case MenuAction.OpenMenu open -> opener.open(viewer, resolve.apply(open.menu()));
            case MenuAction.RunCommand command -> viewer.performCommand(resolve.apply(command.line()));
            case MenuAction.SendMessage message -> viewer.sendMessage(Text.mini(resolve.apply(message.line())));
            case MenuAction.PlaySound sound -> play(viewer, sound);
            case MenuAction.Named named -> named(viewer, named);
        }
    }

    /**
     * A key plays as it is written. A constant is asked of the server, which is the only thing that knows
     * what {@code ITEM_BOOK_PAGE_TURN} is called this release; a name the server does not know plays
     * nothing, exactly as an unknown key does.
     */
    private static void play(Player viewer, MenuAction.PlaySound sound) {
        String name = sound.name();
        if (Key.parseable(name)) {
            viewer.playSound(Sound.sound(Key.key(name), Sound.Source.MASTER, sound.volume(), sound.pitch()));
            return;
        }
        Key key = constant(name);
        if (key != null) {
            viewer.playSound(Sound.sound(key, Sound.Source.MASTER, sound.volume(), sound.pitch()));
        }
        // A sound this server does not have is silence, never a broken click.
    }

    /** The key of the sound the server calls {@code name}, or null when it has no such sound. */
    private static @Nullable Key constant(String name) {
        for (org.bukkit.Sound sound : Registry.SOUND_EVENT) {
            @Nullable Key key = Registry.SOUND_EVENT.getKey(sound);
            if (key != null && name.equals(key.value().replace('.', '_').toUpperCase(Locale.ROOT))) {
                return key;
            }
        }
        return null;
    }

    private void named(Player viewer, MenuAction.Named named) {
        MenuActions.Verb verb = actions.verb(named.name());
        if (verb == null) {
            throw new IllegalStateException("no action is registered under '" + named.name() + "'.");
        }
        verb.run(viewer, resolve.apply(named.argument()));
    }
}
