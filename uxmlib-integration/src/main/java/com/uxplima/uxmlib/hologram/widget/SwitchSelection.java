package com.uxplima.uxmlib.hologram.widget;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The pure first-match selection behind {@link SwitchableHologram}, modelled on the GUI {@code Stateful}
 * item: states are tried in declared order and the first whose {@link State#condition() condition} passes for
 * a {@link ViewerContext} is the one that viewer sees. With no match the selection is empty, so the widget can
 * render nothing (or hide) for that viewer. The payload {@code <T>} is whatever the widget switches between:
 * a hologram, a content spec, an index.
 *
 * @param <T> the value each state carries
 */
public final class SwitchSelection<T> {

    private final List<State<T>> states;

    public SwitchSelection(List<State<T>> states) {
        Objects.requireNonNull(states, "states");
        if (states.isEmpty()) {
            throw new IllegalArgumentException("a switchable needs at least one state");
        }
        this.states = List.copyOf(states);
    }

    /** The value of the first state whose condition passes for {@code context}, or empty when none match. */
    public Optional<T> select(ViewerContext context) {
        Objects.requireNonNull(context, "context");
        for (State<T> state : states) {
            if (state.condition().test(context)) {
                return Optional.of(state.value());
            }
        }
        return Optional.empty();
    }

    /** The ordered states, defensive copy. */
    public List<State<T>> states() {
        return states;
    }

    /**
     * One state of a switchable: the condition that selects it for a viewer and the value it carries.
     *
     * @param <T> the carried value type
     */
    public record State<T>(Predicate<ViewerContext> condition, T value) {
        public State {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(value, "value");
        }
    }
}
