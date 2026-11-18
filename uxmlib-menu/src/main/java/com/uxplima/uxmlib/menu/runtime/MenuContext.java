package com.uxplima.uxmlib.menu.runtime;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * The per-open data a menu binding sees: who is viewing, the optional domain subject the menu was opened for (a warp, a
 * home owner, ...), the current page, (while a list is rendered or clicked) the live list element, any typed command
 * arguments the menu was opened with (an operator {@code command {}} block's {@code %argument_<name>%} values), the
 * menu's own local placeholder definitions, and who triggered the open. The engine creates it; it is public only so
 * feature binding lambdas can read it.
 *
 * <p>The {@code viewer} is who <em>sees</em> the menu: the player it is rendered for, the player whose inventory and
 * permissions a binding reads, and the entity whose thread every render runs on. It is a live {@link Player} because
 * the engine spends it, and a menu cannot be drawn for somebody who is not here.
 *
 * <p>Who <em>triggered</em> the open is not the same question, and the engine does not answer it. An open can be
 * triggered by a player, by the console, or by another plugin, so the trigger has no live {@link Player} to name and no
 * meaning the engine could read. A host that wants to draw the opener puts whatever it wants to say into {@link
 * #passthrough()} and registers its own binding to spend it.
 *
 * <p>The {@code localPlaceholders} map is the open spec's {@code placeholders {}} block: {@code name -> template} pairs
 * the renderer resolves local-first, so a menu can define or override a {@code %name%} token for itself alone. It is
 * empty for a menu that declares no such block, and for every engine child window (a list/confirm/selector/ editor),
 * whose minimal spec carries none.
 *
 * <p>Immutable. {@link #withEntry}, {@link #withPage}, {@link #withPageCount}, {@link #withLocalPlaceholders}, {@link
 * #withTypedLocals}, {@link #withMarkupArguments}, {@link #withPassthrough} and {@link #withPagedViews} return copies
 * rather than mutating, because the same base context is reused across every slot of a list page and must not leak one
 * element's identity into the next; each copy carries the open's arguments, local placeholders, passed-through values
 * and paged-list snapshots through unchanged.
 */
public final class MenuContext {

    private final Player viewer;

    @Nullable private final Object subject;

    private final int page;

    private final int pageCount;

    @Nullable private final Object entry;

    private final Map<String, String> arguments;

    private final Map<String, String> localPlaceholders;

    /**
     * Values the host attached to this open and the engine never reads, keyed by whatever name the host chose. It
     * is empty for every open that attached none, and it survives every {@code with*} copy, so a page flip or a
     * refresh hands a binding back exactly what the open put there.
     *
     * <p>This is deliberately not {@link #arguments()}. Arguments are typed by the player who ran the command, so a
     * host that stored a trusted value among them would let any player claim it by typing it. Nothing a player
     * types reaches this map.
     */
    private final Map<String, Object> passthrough;

    /**
     * The paged lists this render draws, keyed by list-source id: empty for every open that queried no paged source,
     * which is the historic case. A source id present here tells the renderer the list's rows are already one page (so
     * they are placed without re-slicing) and carries the page and corpus total the page indicator reads. Each value is
     * an immutable snapshot taken from the list's {@link ListQueryState} on the viewer's entity thread, so the renderer
     * never touches that mutable state itself.
     */
    private final Map<String, PagedListView> pagedViews;

    /**
     * The argument names whose values the opener vouches for as markup. Every other argument is text a player may
     * have typed, so the renderer escapes it before it goes into a line; one named here goes in as written.
     */
    private final Set<String> markupArguments;

    /**
     * The local placeholder names whose values a player supplied rather than an operator wrote: a prompt answer, a
     * form field, the name of an item a player dropped. Their values sit in {@link #localPlaceholders} with the rest,
     * and this is what tells the renderer to treat them as typed text rather than as a template to expand.
     */
    private final Set<String> typedLocals;

    private MenuContext(
            Player viewer,
            @Nullable Object subject,
            int page,
            int pageCount,
            @Nullable Object entry,
            Map<String, String> arguments,
            Map<String, String> localPlaceholders,
            Map<String, Object> passthrough,
            Map<String, PagedListView> pagedViews,
            Set<String> markupArguments,
            Set<String> typedLocals) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.subject = subject;
        this.page = page;
        this.pageCount = pageCount;
        this.entry = entry;
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.localPlaceholders = Objects.requireNonNull(localPlaceholders, "localPlaceholders");
        this.passthrough = Objects.requireNonNull(passthrough, "passthrough");
        this.pagedViews = Objects.requireNonNull(pagedViews, "pagedViews");
        this.markupArguments = Objects.requireNonNull(markupArguments, "markupArguments");
        this.typedLocals = Objects.requireNonNull(typedLocals, "typedLocals");
    }

    /** Opens a fresh context with no list element bound yet and a single-page count until the renderer knows better. */
    public static MenuContext of(Player viewer, @Nullable Object subject, int page) {
        return of(viewer, subject, page, Map.of());
    }

    /**
     * Opens a fresh context carrying the typed command {@code arguments} the menu was opened with, keyed by
     * argument name so the renderer can expand {@code %argument_<name>%} in a title, name or lore. Kept separate
     * from {@link #of(Player, Object, int)} so the many argument-less opens stay a three-argument call. The local
     * placeholders start empty here; the engine attaches the open spec's block via {@link #withLocalPlaceholders}.
     */
    public static MenuContext of(Player viewer, @Nullable Object subject, int page, Map<String, String> arguments) {
        return new MenuContext(
                viewer,
                subject,
                page,
                1,
                null,
                Map.copyOf(arguments),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Set.of());
    }

    public Player viewer() {
        return viewer;
    }

    public int page() {
        return page;
    }

    /** How many pages the menu's list spans, one-based; the renderer stamps it before drawing static items. */
    public int pageCount() {
        return pageCount;
    }

    /**
     * The typed command arguments the menu was opened with, keyed by argument name: empty for a menu not opened through
     * an argument-carrying command. The renderer reads it to expand {@code %argument_<name>%}; the map is immutable.
     *
     * <p>A value here is what a player typed, so the renderer escapes it where it goes into a line, unless {@link
     * #markupArguments} names it, and keeps it as written where it is read as data: a material, an amount, a condition
     * or an action's argument.
     */
    public Map<String, String> arguments() {
        return arguments;
    }

    /**
     * The open spec's own {@code placeholders {}} block, keyed by custom token name: empty for a menu that declares
     * none. The renderer reads it to resolve a {@code %name%} local-first, ahead of the shared placeholder registry, so
     * a menu-scoped token overrides a built-in or global custom for this menu alone; the map is immutable.
     */
    public Map<String, String> localPlaceholders() {
        return localPlaceholders;
    }

    /**
     * The paged lists this render draws, keyed by list-source id, as immutable snapshots: empty for an open that
     * queried no paged source. The renderer reads it to tell a paged list (already one page) from an in-memory one (the
     * whole corpus, sliced here) and to source the paged list's page numbers, which a host turns into {@code %page%} and
     * {@code %max_page%} by registering {@link com.uxplima.uxmlib.menu.binding.MenuPlaceholders#registerPaging}.
     */
    public Map<String, PagedListView> pagedViews() {
        return pagedViews;
    }

    public Optional<Object> subjectRaw() {
        return Optional.ofNullable(subject);
    }

    public Optional<Object> entry() {
        return Optional.ofNullable(entry);
    }

    /**
     * The domain subject cast to {@code type}. A mismatch means a binding asked for the wrong context, so we
     * fail loudly rather than return null.
     */
    public <T> T subject(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = subjectRaw().orElseThrow(() -> new IllegalStateException("menu has no subject"));
        if (!type.isInstance(value)) {
            throw new IllegalStateException("menu subject is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /** The live list element cast to {@code type}; same fail-loud contract as {@link #subject(Class)}. */
    public <T> T entry(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = entry().orElseThrow(() -> new IllegalStateException("menu has no entry"));
        if (!type.isInstance(value)) {
            throw new IllegalStateException("menu entry is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /** A copy bound to one list element, leaving viewer, subject, page, page count, arguments, locals and passed-through values untouched. */
    public MenuContext withEntry(Object entry) {
        Objects.requireNonNull(entry, "entry");
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                passthrough,
                pagedViews,
                markupArguments,
                typedLocals);
    }

    /** A copy on a new page, used when the renderer or listener advances pagination; resets nothing else. */
    public MenuContext withPage(int page) {
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                passthrough,
                pagedViews,
                markupArguments,
                typedLocals);
    }

    /** A copy carrying the page count the renderer computed, so a registered {@code %max_page%} can read it. */
    public MenuContext withPageCount(int pageCount) {
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                passthrough,
                pagedViews,
                markupArguments,
                typedLocals);
    }

    /**
     * A copy carrying the paged-list snapshots this render draws, keyed by list-source id, attached by the engine on
     * the viewer's entity thread once each paged source has answered and its total is recorded on the list's state.
     * Every other field is untouched, and the map is copied defensively so the caller cannot mutate it afterwards.
     */
    public MenuContext withPagedViews(Map<String, PagedListView> pagedViews) {
        Objects.requireNonNull(pagedViews, "pagedViews");
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                passthrough,
                Map.copyOf(pagedViews),
                markupArguments,
                typedLocals);
    }

    /**
     * A copy carrying the open spec's local {@code placeholders {}} block, attached by the engine when a spec menu
     * opens so the renderer can resolve its custom tokens local-first. Every other field is untouched, and the map is
     * copied defensively so the caller cannot mutate it afterwards.
     */
    public MenuContext withLocalPlaceholders(Map<String, String> localPlaceholders) {
        Objects.requireNonNull(localPlaceholders, "localPlaceholders");
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                Map.copyOf(localPlaceholders),
                passthrough,
                pagedViews,
                markupArguments,
                typedLocals);
    }

    /**
     * The argument names whose values the opener vouched for as markup: empty unless the open named some. The
     * renderer puts these arguments into a line as written and escapes every other one, because an argument is
     * otherwise text a player typed.
     */
    public Set<String> markupArguments() {
        return markupArguments;
    }

    /**
     * A copy in which the arguments under {@code names} are markup the opener vouches for rather than text a player
     * typed. Only an opener that filled those arguments itself may say so: an argument a player typed and that is
     * named here can carry a click event or any other tag into the menu. Every other field is untouched.
     */
    public MenuContext withMarkupArguments(Set<String> names) {
        Objects.requireNonNull(names, "names");
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                passthrough,
                pagedViews,
                Set.copyOf(names),
                typedLocals);
    }

    /**
     * The local placeholder names whose values a player supplied: empty unless a prompt, a form or a dropped item
     * added some through {@link #withTypedLocals}. The renderer escapes these values in a line and never expands a
     * {@code %token%} inside one, and an action reads them as they were typed.
     */
    public Set<String> typedLocals() {
        return typedLocals;
    }

    /**
     * A copy carrying {@code values} as local placeholders a player supplied, layered over the ones already here with
     * the new value winning a clash. They resolve as {@code %name%} like any local placeholder, but as text: a value is
     * never read again for the tokens or tags it contains. Every other field is untouched.
     */
    public MenuContext withTypedLocals(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return this;
        }
        Map<String, String> locals = new LinkedHashMap<>(localPlaceholders);
        locals.putAll(values);
        Set<String> typed = new HashSet<>(typedLocals);
        typed.addAll(values.keySet());
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                Map.copyOf(locals),
                passthrough,
                pagedViews,
                markupArguments,
                Set.copyOf(typed));
    }

    /**
     * The values the host attached to this open, keyed by whatever name the host chose. Empty for an open that
     * attached none; the map is immutable, and the engine reads nothing in it.
     */
    public Map<String, Object> passthrough() {
        return passthrough;
    }

    /**
     * One passed-through value cast to {@code type}. A missing key or a wrong type means a binding asked for
     * something this open never attached, so we fail loudly rather than return null, the same contract as
     * {@link #subject(Class)}.
     */
    public <T> T passthrough(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = passthrough.get(key);
        if (value == null) {
            throw new IllegalStateException("menu open attached no value under: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("passed-through " + key + " is not a " + type.getSimpleName());
        }
        return type.cast(value);
    }

    /**
     * A copy carrying every value in {@code values}, replacing any the open already attached under the same name.
     * Every other field is untouched, and the map is copied defensively so the caller cannot mutate it afterwards.
     */
    public MenuContext withPassthrough(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(passthrough);
        merged.putAll(values);
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                Map.copyOf(merged),
                pagedViews,
                markupArguments,
                typedLocals);
    }

    /**
     * A copy carrying one more passed-through value, replacing any the open already attached under {@code key}.
     * Every other field is untouched, and the value travels through every later copy.
     */
    public MenuContext withPassthrough(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, Object> merged = new LinkedHashMap<>(passthrough);
        merged.put(key, value);
        return new MenuContext(
                viewer,
                subject,
                page,
                pageCount,
                entry,
                arguments,
                localPlaceholders,
                Map.copyOf(merged),
                pagedViews,
                markupArguments,
                typedLocals);
    }
}
