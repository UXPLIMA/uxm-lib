package com.uxplima.uxmlib.gui;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import net.kyori.adventure.text.Component;

/**
 * A bounded, in-memory record of the most recent menu clicks, for debugging a misbehaving menu without
 * attaching a debugger. The click handler appends one {@link Entry} per accepted click; once the buffer is
 * full the oldest entry is dropped, so memory stays fixed. Held on the instance (one per
 * {@link GuiListener}), never statically, and synchronized so a Folia worker and an admin command reading
 * the log do not race.
 *
 * <p>Read it back with {@link #recent()} (newest last). Each entry carries who clicked, the menu title, the
 * slot, and the click type: enough to answer "what did that click do?" after the fact.
 */
public final class GuiClickLog {

    /** Default number of clicks retained when no capacity is given. */
    public static final int DEFAULT_CAPACITY = 100;

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public GuiClickLog() {
        this(DEFAULT_CAPACITY);
    }

    public GuiClickLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    /**
     * Append the click described by {@code event} against {@code menu}. The single call the click handler
     * makes per accepted click; evicts the oldest entry when at capacity. Ignores a click with no slot
     * (outside the window), which carries no useful debugging signal.
     */
    public synchronized void record(Gui menu, InventoryClickEvent event) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(event, "event");
        UUID viewer = event.getWhoClicked().getUniqueId();
        entries.addLast(new Entry(Instant.now(), viewer, menu.title(), event.getSlot(), event.getClick()));
        if (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    /** The retained clicks, oldest first, as an immutable snapshot safe to iterate off the writer thread. */
    public synchronized List<Entry> recent() {
        return List.copyOf(entries);
    }

    /** Drop every retained click. */
    public synchronized void clear() {
        entries.clear();
    }

    /** The number of clicks currently retained. */
    public synchronized int size() {
        return entries.size();
    }

    /** One recorded click: when it happened, who clicked, the menu's title, the slot, and the click type. */
    public record Entry(Instant at, UUID viewer, Component menuTitle, int slot, ClickType clickType) {
        public Entry {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(menuTitle, "menuTitle");
            Objects.requireNonNull(clickType, "clickType");
        }
    }
}
