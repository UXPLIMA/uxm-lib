package com.uxplima.uxmlib.menu.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.spec.ClickKind;
import org.jspecify.annotations.Nullable;

/**
 * What an action binding receives on a click: the per-open {@link MenuContext} plus the live {@link Player} and the
 * gesture that fired. Actions need the live handle (to give items, play sounds, run commands) and the click kind (to
 * branch left vs. shift-right), neither of which belongs on the render-time {@link MenuContext}.
 *
 * <p>Public so feature bindings can read it; created by the engine on each click. A binding that drives the window it
 * fired in (refresh, reset-pagination) reads {@link #control()}, the engine-supplied handle onto the clicked menu.
 *
 * <p>A binding that could not do what it was asked calls {@link #refuse()}, which stops the rest of the gesture's
 * action list. That is what lets a cost be an action of its own rather than something every priced verb has to fold
 * into itself.
 */
public final class MenuActionContext {

    private final MenuContext ctx;

    private final Player player;

    private final ClickKind clickKind;

    private final Map<String, String> args;

    private final MenuControl control;

    /**
     * The arguments as the file wrote them, before any token was filled, when the engine built this context. It is what
     * {@link #textArgs()} fills again for an action that sends its argument as words. Null for a context built from
     * arguments already filled, which can no longer tell a typed value from a written one.
     */
    private final @Nullable Map<String, String> written;

    /**
     * Whether the action refused. Written by the handler and read by the engine that called it, both on the same
     * thread and with nothing in between, so a plain field says exactly what happens.
     */
    private boolean refused;

    /**
     * The constructor every non-listener call-site uses: a context with no menu-control handle, so a control action
     * invoked through it is a safe no-op. Delegates to the canonical constructor with {@link MenuControl#NOOP}, which
     * keeps the existing call-sites (feature bindings, unit tests) compiling unchanged: only the live click path needs
     * the five-argument form below.
     */
    public MenuActionContext(MenuContext ctx, Player player, ClickKind clickKind, Map<String, String> args) {
        this(ctx, player, clickKind, args, MenuControl.NOOP);
    }

    /** The canonical constructor: the same context plus the engine's handle onto the menu the click fired in. */
    public MenuActionContext(
            MenuContext ctx, Player player, ClickKind clickKind, Map<String, String> args, MenuControl control) {
        this(ctx, player, clickKind, Map.copyOf(Objects.requireNonNull(args, "args")), null, control);
    }

    private MenuActionContext(
            MenuContext ctx,
            Player player,
            ClickKind clickKind,
            Map<String, String> args,
            @Nullable Map<String, String> written,
            MenuControl control) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.player = Objects.requireNonNull(player, "player");
        this.clickKind = Objects.requireNonNull(clickKind, "clickKind");
        this.args = args;
        this.written = written;
        this.control = Objects.requireNonNull(control, "control");
    }

    /**
     * A context for an action ref whose arguments are {@code written} as the file wrote them. They are filled from
     * {@code ctx} in one pass for {@link #args()}, as data, and kept so {@link #textArgs()} can fill them again as
     * words. This is the form the engine uses.
     */
    public static MenuActionContext written(
            MenuContext ctx, Player player, ClickKind clickKind, Map<String, String> written, MenuControl control) {
        Map<String, String> asWritten = Map.copyOf(Objects.requireNonNull(written, "written"));
        Objects.requireNonNull(ctx, "ctx");
        return new MenuActionContext(
                ctx, player, clickKind, Map.copyOf(ActionArguments.resolve(asWritten, ctx)), asWritten, control);
    }

    public Player viewer() {
        return ctx.viewer();
    }

    /**
     * The per-open context this click happened in. Most bindings read the delegating accessors below instead; the
     * whole context is exposed for the developer-API boundary, which hands a click handler the same view a render
     * handler sees.
     */
    public MenuContext context() {
        return ctx;
    }

    public Player player() {
        return player;
    }

    public ClickKind clickKind() {
        return clickKind;
    }

    /**
     * The engine's handle onto the menu this click fired in: what a {@code refresh}/{@code reset-pagination} action
     * drives the window through. A context built outside a live click carries {@link MenuControl#NOOP}, so reading it
     * is always safe.
     */
    public MenuControl control() {
        return control;
    }

    public int page() {
        return ctx.page();
    }

    public <T> T subject(Class<T> type) {
        return ctx.subject(type);
    }

    public <T> T entry(Class<T> type) {
        return ctx.entry(type);
    }

    /** The invoked action ref's arguments: {@code "command:spawn"} arrives as {@code {value: "spawn"}}. */
    public Map<String, String> args() {
        return args;
    }

    /** The single positional argument of an {@code id:value} action ref, or empty when the ref carried none. */
    public String arg() {
        return args.getOrDefault("value", "");
    }

    /**
     * The arguments filled as a line of words rather than as data: every value a player typed escaped so MiniMessage
     * reads it as what they typed, every value an operator wrote as written. An action that parses its argument as
     * MiniMessage and sends it reads this instead of {@link #args()}, or a player who types a tag into a menu argument
     * or a prompt has it drawn. A context built from arguments already filled answers {@link #args()}.
     */
    public Map<String, String> textArgs() {
        Map<String, String> source = written;
        return source == null ? args : ActionArguments.resolveAsText(source, ctx);
    }

    /**
     * The arguments filled with every value a player typed put through {@code typed}, and every value an operator
     * wrote, or an argument the opener vouched for as markup, as written. An action that sends its argument in a format
     * other than MiniMessage reads this with that format's escape: JSON string content for a JSON component, the colour
     * codes taken out for a legacy line. A context built from arguments already filled answers {@link #args()}.
     */
    public Map<String, String> args(UnaryOperator<String> typed) {
        Objects.requireNonNull(typed, "typed");
        Map<String, String> source = written;
        return source == null ? args : ActionArguments.resolve(source, ctx, typed);
    }

    /** The single positional argument filled as words, as {@link #textArgs()} fills it. */
    public String textArg() {
        return textArgs().getOrDefault("value", "");
    }

    /**
     * Refuse: this action could not do what it was asked, and nothing written after it in the same list may run.
     *
     * <p>It is what lets a cost be an action of its own. A file writes {@code ["shop:cost", "shop:give"]} and means
     * the second only if the first worked, and until an action could say so the engine had no way to hear it: the
     * charge and the hand-over had to be folded into one verb, so every plugin that priced anything wrote a second
     * verb per priced thing.
     *
     * <p>Call it and return. It is not an exception and it says nothing to the player: a refusal that a viewer should
     * read is a {@code message} the action sends before it refuses, or the gesture's own {@code deny} list. Calling
     * it twice, or on a context outside a live click (a test, a binding invoked directly), is harmless: the flag is
     * simply read by whoever is walking the list, and nobody is.
     *
     * <p>One thing it cannot stop, and the reason is worth knowing: an action carrying a {@code delay} is waited off
     * the list and fires on its own, so the list has already moved past it by the time it runs. A cost with a delay
     * on it stops nothing. Write a cost without one.
     */
    public void refuse() {
        this.refused = true;
    }

    /**
     * Whether {@link #refuse()} was called on this context.
     *
     * <p>Read by the engine on the thread that ran the handler, immediately after it returns, so there is nothing
     * here to publish across threads. An action that hands its work to another thread and refuses from there refuses
     * nothing: the list has already gone on.
     */
    public boolean refused() {
        return refused;
    }
}
