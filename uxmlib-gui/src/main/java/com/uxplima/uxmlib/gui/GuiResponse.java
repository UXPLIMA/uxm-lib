package com.uxplima.uxmlib.gui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.item.GuiItem;

/**
 * One side effect of a click, expressed as data instead of a direct mutation. A declarative click handler
 * returns a {@code List<GuiResponse>} and the framework applies them in order on the viewer's region thread
 * (see {@link GuiResponses}). Modelling effects as a closed set of values keeps the handler a pure function
 * (it decides <em>what</em> should happen and the engine decides <em>how</em>), which makes the handler
 * unit-testable without a live inventory and safe to compute off-thread.
 *
 * <p>Use the factories ({@link #close()}, {@link #open(Gui)}, {@link #refresh()}, {@link #updateItem(int,
 * GuiItem)}, {@link #replaceCursor(ItemStack)}, {@link #playSound(Sound)}, {@link #run(Runnable)},
 * {@link #nothing()}). Pattern from AnvilGUI's {@code ResponseAction} (MIT): the click result is a list of
 * composable actions applied in order, rather than the handler mutating the menu directly.
 */
public sealed interface GuiResponse
        permits GuiResponse.Close,
                GuiResponse.Open,
                GuiResponse.Refresh,
                GuiResponse.UpdateItem,
                GuiResponse.ReplaceCursor,
                GuiResponse.PlaySound,
                GuiResponse.Run,
                GuiResponse.Nothing {

    /** Close the menu for the viewer. */
    static GuiResponse close() {
        return Close.INSTANCE;
    }

    /** Open {@code gui} for the viewer (replacing the current menu). */
    static GuiResponse open(Gui gui) {
        return new Open(gui);
    }

    /** Re-resolve and rewrite the current menu for the viewer (e.g. after a stateful change). */
    static GuiResponse refresh() {
        return Refresh.INSTANCE;
    }

    /** Place {@code item} at {@code slot} of the current menu. */
    static GuiResponse updateItem(int slot, GuiItem item) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be >= 0");
        }
        return new UpdateItem(slot, item);
    }

    /** Replace the item on the viewer's cursor with {@code cursor}. */
    static GuiResponse replaceCursor(ItemStack cursor) {
        return new ReplaceCursor(cursor);
    }

    /** Play {@code sound} to the viewer. */
    static GuiResponse playSound(Sound sound) {
        return new PlaySound(sound);
    }

    /** Run an arbitrary {@code task} on the viewer's region thread (the escape hatch for custom effects). */
    static GuiResponse run(Runnable task) {
        return new Run(task);
    }

    /** Do nothing; the shared instance avoids allocating for a handler that opts out. */
    static GuiResponse nothing() {
        return Nothing.INSTANCE;
    }

    /** Wrap {@code responses} in an already-complete future: the sync convenience for a declarative handler. */
    static CompletableFuture<List<GuiResponse>> completed(List<GuiResponse> responses) {
        Objects.requireNonNull(responses, "responses");
        return CompletableFuture.completedFuture(List.copyOf(responses));
    }

    /** Close the menu for the viewer. */
    record Close() implements GuiResponse {
        static final Close INSTANCE = new Close();
    }

    /** Open another menu for the viewer. */
    record Open(Gui gui) implements GuiResponse {
        public Open {
            Objects.requireNonNull(gui, "gui");
        }
    }

    /** Refresh the current menu for the viewer. */
    record Refresh() implements GuiResponse {
        static final Refresh INSTANCE = new Refresh();
    }

    /** Replace the item at a slot of the current menu. */
    record UpdateItem(int slot, GuiItem item) implements GuiResponse {
        public UpdateItem {
            Objects.requireNonNull(item, "item");
        }
    }

    /** Replace the item on the viewer's cursor. */
    record ReplaceCursor(ItemStack cursor) implements GuiResponse {
        public ReplaceCursor {
            Objects.requireNonNull(cursor, "cursor");
            cursor = cursor.clone();
        }
    }

    /** Play a sound to the viewer. */
    record PlaySound(Sound sound) implements GuiResponse {
        public PlaySound {
            Objects.requireNonNull(sound, "sound");
        }
    }

    /** Run a custom task on the viewer's region thread. */
    record Run(Runnable task) implements GuiResponse {
        public Run {
            Objects.requireNonNull(task, "task");
        }
    }

    /** The do-nothing response. */
    record Nothing() implements GuiResponse {
        static final Nothing INSTANCE = new Nothing();
    }
}
