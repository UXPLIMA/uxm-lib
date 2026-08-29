package com.uxplima.uxmlib.gui.item;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.event.inventory.InventoryClickEvent;

import com.uxplima.uxmlib.gui.ClickContext;
import com.uxplima.uxmlib.gui.GuiResponse;

/**
 * What happens when a {@link GuiItem} is clicked. A sealed set: {@link Run} carries an imperative handler,
 * {@link None} does nothing, {@link Responding} carries a declarative (possibly async) handler, so click
 * routing pattern-matches over a closed set instead of guarding a nullable callback, and a "no action" slot
 * is explicit rather than a silent null.
 *
 * <p>{@link Run} (the simple, imperative path) and {@link Responding} (the advanced, pure, unit-testable
 * path) are both first-class: a click handler can mutate the event directly, or return a list of
 * {@link GuiResponse}s the framework applies. The two contracts coexist; neither replaces the other.
 */
public sealed interface GuiAction permits GuiAction.Run, GuiAction.None, GuiAction.Responding {

    /**
     * Run the imperative action for {@code event}. The framework has already cancelled the event by
     * default. For {@link Responding} this is a no-op: routing invokes its {@link Responding#handler()}
     * with an immutable {@link ClickContext} instead of mutating the live event here.
     */
    void accept(InventoryClickEvent event);

    /** An action that runs a handler. */
    record Run(Consumer<InventoryClickEvent> handler) implements GuiAction {
        public Run {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public void accept(InventoryClickEvent event) {
            handler.accept(event);
        }
    }

    /** The do-nothing action; the shared {@link #INSTANCE} avoids allocating per display item. */
    record None() implements GuiAction {
        public static final None INSTANCE = new None();

        @Override
        public void accept(InventoryClickEvent event) {
            // Intentionally empty: a display item has no behaviour.
        }
    }

    /**
     * A declarative action: its handler is a pure function from an immutable {@link ClickContext} to a
     * future list of {@link GuiResponse}s the framework applies in order. A synchronous handler returns an
     * already-complete future (via {@link GuiResponse#completed}); an asynchronous one computes off-thread
     * and the responses are applied back on the viewer's region thread. The handler never touches Bukkit.
     */
    record Responding(Function<ClickContext, CompletableFuture<List<GuiResponse>>> handler) implements GuiAction {
        public Responding {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public void accept(InventoryClickEvent event) {
            // Intentionally empty: the declarative variant is dispatched via handler(), not accept(event).
        }
    }
}
