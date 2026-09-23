package com.uxplima.uxmlib.menu;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.style.SoundNames;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.ListControlSyntax;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.NullMarked;

/**
 * The actions and conditions that mean the same thing in every menu of every plugin, registered on one call.
 *
 * <p>{@code close}, {@code open:<menu>}, {@code command:<line>}, {@code message:<line>} and
 * {@code sound:<name> <volume> <pitch>}, and {@code perm:<node>}. They are here so that a plugin which only wants
 * a working menu file does not write them again, and so that an operator who has written one menu can write any
 * of them.
 *
 * <p>{@code perm} is here because the loader writes it. An item's {@code permission = "<node>"} becomes a
 * {@code perm:<node>} view requirement, and until 0.111.0 no plugin but uxmEssentials answered it, so an item
 * gated that way was hidden from every viewer: an unregistered condition fails closed.
 *
 * <p>These are mechanisms and not a look. Nothing here decides a colour, a word, or a layout: the file says
 * what to run and the plugin says what its own verbs mean. That is why the library may hold them.
 *
 * <p>Registration is a call and never automatic. Each verb is an offer ({@link MenuBindings#defaultAction}): a plugin
 * that wants its own {@code sound}, its own {@code command} or its own {@code has-next} registers it under the same
 * name, before this call or after it, and its own is the one that runs.
 *
 * <p>Every verb runs on the thread of the click, which the engine has already put on the viewer's entity
 * thread. Nothing here reaches for a scheduler, so this is safe on Folia.
 */
@NullMarked
public final class MenuBasics {

    /** What a volume and a pitch are when the file writes neither, and when it writes something that is not a number. */
    private static final float DEFAULT = 1.0F;

    private MenuBasics() {}

    /**
     * Register the four verbs that need nothing but the viewer: {@code close}, {@code command},
     * {@code message} and {@code sound}, the {@code perm} condition, and the list verbs and page conditions
     * ({@code list-sort}, {@code list-filter}, {@code list-search}, {@code has-next}, {@code has-previous}).
     *
     * <p>{@code open} is not among them, because opening a menu needs the engine that holds the menus. A
     * plugin with more than one window uses {@link #register(MenuBindings, Menus)} instead.
     */
    public static void register(MenuBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        bindings.defaultAction("close", ctx -> ctx.player().closeInventory());
        bindings.defaultAction("command", ctx -> ctx.player().performCommand(ctx.arg()));
        bindings.defaultAction("message", ctx -> ctx.player().sendMessage(Text.mini(ctx.arg())));
        bindings.defaultAction("sound", MenuBasics::sound);
        bindings.defaultCondition("perm", MenuBasics::holds);
        registerListControls(bindings);
    }

    /**
     * The three list verbs and the page arrows' conditions. The window syntax has had {@code list-sort},
     * {@code list-filter} and {@code list-search} ({@link ListControlSyntax}) and the engine carries them out through
     * {@link com.uxplima.uxmlib.menu.runtime.MenuControl}, but until 0.130.0 nothing registered them, so every sort,
     * filter, search and tab button was a dead click in a plugin that did not write its own. {@code has-next} and
     * {@code has-previous} (and {@code has-prev}, which uxmEssentials' windows write) read the page the window is on.
     * A line that does not parse does nothing, as an unknown list does.
     */
    private static void registerListControls(MenuBindings bindings) {
        bindings.defaultAction(
                ListControlSyntax.SORT_ACTION,
                ctx -> ListControlSyntax.parseSort(ctx.arg())
                        .ifPresent(ref -> ctx.control().sortList(ref.listId(), ref.direction())));
        bindings.defaultAction(
                ListControlSyntax.FILTER_ACTION,
                ctx -> ListControlSyntax.parseFilter(ctx.arg())
                        .ifPresent(ref -> ctx.control().filterList(ref.listId(), ref.key(), ref.value())));
        bindings.defaultAction(
                ListControlSyntax.SEARCH_ACTION,
                ctx -> ListControlSyntax.parseSearch(ctx.arg())
                        .ifPresent(ref -> ctx.control().searchList(ref.listId(), ref.key())));
        bindings.defaultCondition("has-next", (ctx, args) -> ctx.page() + 1 < ctx.pageCount());
        bindings.defaultCondition("has-previous", (ctx, args) -> ctx.page() > 0);
        bindings.defaultCondition("has-prev", (ctx, args) -> ctx.page() > 0);
    }

    /** Whether the viewer holds the node the line names. A blank node is held by nobody, operators included. */
    private static boolean holds(MenuContext ctx, Map<String, String> args) {
        String node = args.getOrDefault("value", "").strip();
        return !node.isEmpty() && ctx.viewer().hasPermission(node);
    }

    /** The same four verbs, and {@code open:<menu>} on top of them. */
    public static void register(MenuBindings bindings, Menus menus) {
        Objects.requireNonNull(menus, "menus");
        register(bindings);
        bindings.defaultAction("open", ctx -> menus.open(ctx.player(), ctx.arg().strip(), null));
    }

    /**
     * Play what the line names, to the viewer alone.
     *
     * <p>A name this server does not have is silence. So is a volume or a pitch that is not a number, which
     * falls back to one rather than throwing: this runs under a player's cursor, and a click that throws is
     * worse than a click that is quiet. The line was read from a file that an operator may edit while the
     * server runs, so there is no earlier moment at which to refuse it.
     */
    private static void sound(MenuActionContext ctx) {
        String[] parts = ctx.arg().strip().split("\\s+");
        SoundNames.key(parts[0]).ifPresent(key -> play(ctx.player(), key, number(parts, 1), number(parts, 2)));
    }

    private static void play(Player viewer, Key key, float volume, float pitch) {
        viewer.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
    }

    private static float number(String[] parts, int at) {
        if (at >= parts.length) {
            return DEFAULT;
        }
        try {
            return Float.parseFloat(parts[at]);
        } catch (NumberFormatException notANumber) {
            return DEFAULT;
        }
    }
}
