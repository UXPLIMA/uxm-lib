package com.uxplima.uxmlib.menu.property;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A property whose click cycles through a small, fixed set of states, a boolean or a short enum, and hands
 * the chosen state to a setter. A left-click advances to the next state, a right-click steps back, so the viewer can
 * move either way through the cycle without re-opening anything. The new state is written through the caller's setter
 * off the tick thread via the shared {@link Scheduler}, then the editor is redrawn so the value lore shows the new
 * state.
 *
 * <p>The setter is the module's existing application use case wrapped as a {@link Consumer}; this property holds no
 * domain logic. The value-display function resolves a state to its locale display string (the caller routes it through
 * the message catalog), so no state name is ever an inline literal.
 *
 * @param <S> the state type cycled (e.g. {@link Boolean} or an enum)
 */
@NullMarked
public final class ToggleProperty<S> implements EditableProperty {

    private final String label;
    private final Material icon;
    private final List<S> states;
    private final Supplier<S> current;
    private final BiFunction<Player, S, String> display;
    private final Consumer<S> setter;
    private final Scheduler scheduler;

    /** The state already painted, for a caller that hands one over; null for the plain-text caller. */
    @Nullable private final BiFunction<Player, S, Component> painted;

    /** This button's own click line, or empty to take the editor's. */
    private final String action;

    public ToggleProperty(
            String label,
            Material icon,
            List<S> states,
            Supplier<S> current,
            BiFunction<Player, S, String> display,
            Consumer<S> setter,
            Scheduler scheduler) {
        this(label, icon, states, current, display, null, "", setter, scheduler);
    }

    /**
     * The same, with the state handed over already painted and with a click line of this button's own.
     *
     * <p>A state is a coloured word by UI-STYLE 9, and the plain-text constructor has nowhere to put the
     * colour: a caller that answered {@code "<good>On"} had those characters printed on the button, because
     * the editor puts a value through the catalogue and the catalogue inserts a value as text. This is the
     * constructor for a caller that has a component in hand, which is every caller that reads its words out
     * of a message catalogue.
     *
     * <p>A separate constructor and not an overload: both display functions erase to {@code BiFunction}.
     */
    public static <S> ToggleProperty<S> painted(
            String label,
            Material icon,
            List<S> states,
            Supplier<S> current,
            BiFunction<Player, S, Component> painted,
            String action,
            Consumer<S> setter,
            Scheduler scheduler) {
        Objects.requireNonNull(painted, "painted");
        return new ToggleProperty<>(
                label,
                icon,
                states,
                current,
                (viewer, state) -> PlainTextComponentSerializer.plainText().serialize(painted.apply(viewer, state)),
                painted,
                action,
                setter,
                scheduler);
    }

    /** A two-state on/off toggle whose two words arrive painted, with a click line of its own. */
    public static ToggleProperty<Boolean> ofBooleanPainted(
            String label,
            Material icon,
            Supplier<Boolean> current,
            BiFunction<Player, Boolean, Component> painted,
            String action,
            Consumer<Boolean> setter,
            Scheduler scheduler) {
        return painted(label, icon, List.of(Boolean.FALSE, Boolean.TRUE), current, painted, action, setter, scheduler);
    }

    private ToggleProperty(
            String label,
            Material icon,
            List<S> states,
            Supplier<S> current,
            BiFunction<Player, S, String> display,
            @Nullable BiFunction<Player, S, Component> painted,
            String action,
            Consumer<S> setter,
            Scheduler scheduler) {
        this.painted = painted;
        this.action = Objects.requireNonNull(action, "action");
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.states = List.copyOf(Objects.requireNonNull(states, "states"));
        if (this.states.size() < 2) {
            throw new IllegalArgumentException("a toggle needs at least two states, had " + this.states.size());
        }
        this.current = Objects.requireNonNull(current, "current");
        this.display = Objects.requireNonNull(display, "display");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** A two-state on/off toggle: the common boolean case. */
    public static ToggleProperty<Boolean> ofBoolean(
            String label,
            Material icon,
            Supplier<Boolean> current,
            BiFunction<Player, Boolean, String> display,
            Consumer<Boolean> setter,
            Scheduler scheduler) {
        return new ToggleProperty<>(
                label, icon, List.of(Boolean.FALSE, Boolean.TRUE), current, display, setter, scheduler);
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return display.apply(viewer, current.get());
    }

    @Override
    public Optional<Component> drawnValue(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return painted == null ? Optional.empty() : Optional.of(painted.apply(viewer, current.get()));
    }

    @Override
    public String action() {
        return action;
    }

    @Override
    public void onClick(PropertyClick click) {
        Objects.requireNonNull(click, "click");
        S next = step(click.rightClick() ? -1 : 1);
        scheduler.async(() -> {
            setter.accept(next);
            scheduler.entity(click.viewer(), click.reopen());
        });
    }

    private S step(int direction) {
        int index = states.indexOf(current.get());
        int base = index < 0 ? 0 : index;
        int size = states.size();
        int target = ((base + direction) % size + size) % size;
        return states.get(target);
    }
}
