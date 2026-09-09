package com.uxplima.uxmlib.condition.action;

import java.util.Objects;

/**
 * A config action string after parsing: the recognised {@link ActionType}, the raw payload that followed the
 * prefix (empty for a payload-less action such as {@code [close]}), and the {@link Action} closure built from
 * them. Exposing the type and payload alongside the closure lets a test assert the parse routed to the right
 * kind without having to run the side effect, while still running the closure against a fake context to assert
 * the effect itself.
 */
public record ParsedAction(ActionType type, String payload, Action action, ActionModifiers modifiers) {

    /** The form from before an action could carry modifiers, which is one that runs once and at once. */
    public ParsedAction(ActionType type, String payload, Action action) {
        this(type, payload, action, ActionModifiers.none());
    }

    /** Canonical constructor null-checks every component. */
    public ParsedAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(modifiers, "modifiers");
    }
}
