package com.uxplima.uxmlib.gui;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Shared menu behaviour: holds the slot→item map, lazily builds the backing inventory (so {@code this}
 * never escapes a constructor), renders items into it, and routes clicks. Clicks inside the menu are
 * cancelled before the slot action runs, so an unconfigured menu cannot leak items.
 */
abstract class AbstractGui implements Gui {

    private Component title;
    private final int size;
    private final @Nullable GuiType type;
    // Owns the slot→item map and the slot-mutation API over the fixed slot count.
    private final GuiSlots slots;
    private @Nullable Inventory inventory;
    // Owns the open/close/click handler fields and the reopen / prevent-close / programmatic-close state.
    private final GuiHandlers handlers = new GuiHandlers();
    // Owns the attached animations, the animation clock, and the auto-refresh cadence.
    private final GuiAnimations animations = new GuiAnimations();
    // Owns the allowed interaction classes and the click/drag cancel policy that consults them.
    private final GuiInteractions interactions = new GuiInteractions();
    // Drives the viewer lifecycle (open/close/handleClose and the scheduler-backed retry/reopen). Takes the
    // menu per call rather than a back-reference, so this does not escape the constructor.
    private final GuiViewing viewing = new GuiViewing(animations, handlers);
    private GuiSound sounds = GuiSound.NONE;

    AbstractGui(Component title, int rows) {
        this.title = Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6");
        }
        this.size = rows * 9;
        this.type = null;
        this.slots = new GuiSlots(size);
    }

    AbstractGui(Component title, GuiType type) {
        this.title = Objects.requireNonNull(title, "title");
        this.type = Objects.requireNonNull(type, "type");
        this.size = type.size();
        this.slots = new GuiSlots(size);
    }

    @Override
    public final Component title() {
        return title;
    }

    @Override
    public final int size() {
        return size;
    }

    @Override
    public void set(int slot, GuiItem item) {
        slots.set(slot, item, this, inventory);
    }

    @Override
    public void set(int row, int col, GuiItem item) {
        slots.set(row, col, item, this);
    }

    @Override
    public void addItem(GuiItem... newItems) {
        slots.addItem(this, newItems);
    }

    @Override
    public @Nullable GuiItem getItem(int slot) {
        return slots.get(slot);
    }

    @Override
    public GuiFiller filler() {
        return new GuiFiller(this);
    }

    @Override
    public void addAnimation(SlotAnimation animation) {
        animations.add(animation, inventory, slots.map());
    }

    @Override
    public Gui allow(InteractionModifier modifier) {
        interactions.allow(modifier);
        return this;
    }

    @Override
    public Gui disallow(InteractionModifier modifier) {
        interactions.disallow(modifier);
        return this;
    }

    @Override
    public boolean allows(InteractionModifier modifier) {
        return interactions.allows(modifier);
    }

    @Override
    public void remove(int slot) {
        slots.remove(slot, inventory);
    }

    @Override
    public void clear() {
        slots.clear(inventory);
    }

    @Override
    public void onClose(Consumer<InventoryCloseEvent> handler) {
        handlers.onClose(handler);
    }

    @Override
    public void onOpen(Consumer<InventoryOpenEvent> handler) {
        handlers.onOpen(handler);
    }

    @Override
    public void onDefaultClick(Consumer<InventoryClickEvent> handler) {
        handlers.onDefaultClick(handler);
    }

    @Override
    public void onOutsideClick(Consumer<InventoryClickEvent> handler) {
        handlers.onOutsideClick(handler);
    }

    @Override
    public void handleOpen(InventoryOpenEvent event) {
        GuiRegistry.onOpen(this);
        if (handlers.reopening()) {
            return; // an internal title-change reopen, not a user-visible open
        }
        sounds.playOpen(event.getPlayer());
        handlers.fireOpen(event);
    }

    /** Whether this menu has animated or auto-refresh content and is currently being viewed. */
    boolean needsTicking() {
        return animations.needsTicking(inventory, slots.map());
    }

    final boolean hasAnimatedContent() {
        return animations.hasAnimatedContent(slots.map());
    }

    @Override
    public void open(HumanEntity viewer) {
        viewing.open(this, viewer);
    }

    @Override
    public void close(HumanEntity viewer) {
        viewing.close(this, viewer);
    }

    @Override
    public void closeAll() {
        viewing.closeAll(this);
    }

    @Override
    public Gui preventClose(boolean prevent) {
        handlers.preventClose(prevent);
        return this;
    }

    @Override
    public boolean preventsClose() {
        return handlers.preventsClose();
    }

    @Override
    public void updateTitle(Component newTitle) {
        Objects.requireNonNull(newTitle, "title");
        this.title = newTitle;
        Inventory old = inventory;
        if (old == null) {
            return; // not built yet; the new title will be used when it is created
        }
        // Bukkit fixes a title at creation, so rebuild and reopen the inventory under the reopen guard, so
        // the internal close/reopen does not fire the user's handlers or the open sound.
        this.inventory = null;
        handlers.runReopen(() -> GuiRender.reopen(old, getInventory()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Cancel synchronously inside the event; the slot action can run inline or be deferred a tick.
        applyClickPolicy(event);
        dispatchClick(event);
    }

    /** Cancel the event with {@code DENY} unless the menu allows its interaction; must run in-event. */
    final void applyClickPolicy(InventoryClickEvent event) {
        interactions.applyPolicy(event);
    }

    /** Run the clicked slot's action (and click sound). May be deferred to the next tick by the listener. */
    final void dispatchClick(InventoryClickEvent event) {
        @Nullable Scheduler scheduler = GuiRegistry.installedScheduler();
        boolean hitItem = GuiClick.dispatch(
                this, inventory, slots.map(), handlers.defaultClick(), handlers.outsideClick(), scheduler, event);
        if (hitItem) {
            sounds.playClick(event.getWhoClicked());
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        interactions.routeDrag(event);
    }

    /** Set the click/open feedback sounds for this menu. */
    final void sounds(GuiSound newSounds) {
        this.sounds = Objects.requireNonNull(newSounds, "sounds");
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        viewing.handleClose(this, event);
    }

    @Override
    public final Inventory getInventory() {
        Inventory inv = inventory;
        if (inv == null) {
            inv = type == null
                    ? Bukkit.createInventory(this, size, title)
                    : Bukkit.createInventory(this, type.inventoryType(), title);
            inventory = inv;
            render(inv);
        }
        return inv;
    }

    /** The item map, for subclasses that render derived content (e.g. pagination). */
    final Map<Integer, GuiItem> items() {
        return slots.map();
    }

    /** The live inventory if it has been built, else {@code null}. */
    final @Nullable Inventory liveInventory() {
        return inventory;
    }

    private void render(Inventory inv) {
        GuiRender.renderAll(inv, this, slots.map(), GuiRender.firstViewer(inv));
    }

    @Override
    public long ticks() {
        return animations.ticks();
    }

    /**
     * Re-resolve every item for the current viewer and rewrite the open inventory in place. Note: a menu
     * with dynamic/stateful/animated content is single-viewer: open one instance per player (a navigator
     * does this for you). With a shared inventory only the static items are correct for every viewer.
     */
    @Override
    public void refresh() {
        Inventory inv = inventory;
        if (inv != null) {
            render(inv);
        }
    }

    /** Advance the animation clock and re-render the changeable items on the configured interval. */
    final void tick() {
        animations.tick(inventory, this, slots.map());
    }

    /** Set how often this menu re-renders while open ({@code null} = every tick, for animations). */
    final void autoRefresh(@Nullable Duration interval) {
        animations.autoRefresh(interval);
    }
}
