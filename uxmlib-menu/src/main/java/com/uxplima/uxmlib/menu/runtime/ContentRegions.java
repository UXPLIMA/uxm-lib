package com.uxplima.uxmlib.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.binding.ContentProviderRegistry;
import com.uxplima.uxmlib.menu.providers.ContentClick;
import com.uxplima.uxmlib.menu.providers.ContentProvider;
import com.uxplima.uxmlib.menu.providers.OwnRowsClick;
import com.uxplima.uxmlib.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The live-item regions of a menu: what a click or a drag in one may do, and what the region holds when the window
 * closes. Each question goes to the {@link ContentProvider} registered for the region; a region with no provider
 * refuses every click and hands nothing back.
 */
@NullMarked
final class ContentRegions {

    @Nullable private final ContentProviderRegistry contents;

    ContentRegions(@Nullable ContentProviderRegistry contents) {
        this.contents = contents;
    }

    /**
     * Route a click that lands on (or into) one of the menu's {@code content {}} regions, and report whether it was
     * handled here. The event arrives already cancelled, so every path that simply returns leaves the region as
     * untouchable as any chrome slot: that is the default for a region that is not editable, whose provider is not
     * registered, or whose provider refuses this one movement. Only an editable region whose provider says yes has the
     * cancel lifted, which is the single place an item may move inside a menu window.
     *
     * <p>A shift-click from the viewer's own inventory is handled rather than delegated, because vanilla would scatter
     * the stack across whatever top slots happen to be free (chrome gaps included) instead of into the region. The
     * engine therefore performs that insert itself, into the first free slot of a region that accepts it.
     *
     * <p>Two gestures are refused outright even in an editable region: a double-click, which vanilla resolves by
     * gathering matching stacks from the <em>whole</em> window (chrome tiles are not real items, so gathering them
     * would mint items), and a hotbar swap, whose source slot is outside the region the provider is answering about.
     */
    boolean routeClick(MenuHolder holder, InventoryClickEvent event) {
        if (holder.spec().contents().isEmpty()) {
            return false;
        }
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (raw >= 0 && raw < topSize) {
            ContentRegionSpec region = holder.spec().regionAt(raw).orElse(null);
            if (region == null) {
                return false;
            }
            handleContentClick(holder, region, event, raw);
            return true;
        }
        if (raw >= topSize && handOwnRowsClick(holder, event)) {
            return true;
        }
        if (raw >= topSize && event.isShiftClick()) {
            handleContentShiftInsert(holder, event, topSize);
            return true;
        }
        if (raw >= topSize && letsOwnRowsMove(holder, event)) {
            event.setCancelled(false);
            return true;
        }
        return false;
    }

    /**
     * Put a click on a stack in the viewer's own rows to each region's provider, and report whether one took it as its
     * own move. A gesture that reaches beyond the one slot is put to none of them.
     */
    private boolean handOwnRowsClick(MenuHolder holder, InventoryClickEvent event) {
        ItemStack stack = emptyToNull(event.getCurrentItem());
        if (stack == null || refusedGesture(event)) {
            return false;
        }
        OwnRowsClick click = new OwnRowsClick(event.getSlot(), stack.clone(), MenuListener.kindOf(event.getClick()));
        for (ContentRegionSpec region : holder.spec().contents().values()) {
            ContentProvider provider = contentProvider(region);
            if (provider != null && provider.ownRowsClicked(holder.ctx(), region, click)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a plain click in the viewer's own rows may go through. In a window that holds items it must: a stack is
     * picked up there and put down in the region, and a window that refused the pick up left a player only the shift
     * click, so a vault page could not take half a stack. The click moves nothing in the window itself, and the put
     * down that follows is a click on the region, asked of its provider like any other. A canvas over the viewer's
     * own rows keeps the blanket cancel, because those rows are its tiles, and so does a gesture that reaches beyond
     * the one slot.
     */
    private boolean letsOwnRowsMove(MenuHolder holder, InventoryClickEvent event) {
        if (holder.spec().bottomInventory() || refusedGesture(event)) {
            return false;
        }
        for (ContentRegionSpec region : holder.spec().contents().values()) {
            if (region.editable() && contentProvider(region) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ask the region's provider about this one movement and lift the blanket cancel when it allows it, or hand the
     * click of a read-only region to its provider, which carries it out on its own record.
     */
    private void handleContentClick(MenuHolder holder, ContentRegionSpec region, InventoryClickEvent event, int slot) {
        ContentProvider provider = contentProvider(region);
        if (provider == null || refusedGesture(event)) {
            return;
        }
        ItemStack cursor = emptyToNull(event.getCursor());
        ItemStack current = emptyToNull(event.getCurrentItem());
        ContentClick.Kind kind;
        if (event.isShiftClick() || cursor == null) {
            kind = ContentClick.Kind.TAKE;
        } else if (current == null) {
            kind = ContentClick.Kind.INSERT;
        } else {
            kind = ContentClick.Kind.SWAP;
        }
        ContentClick click = new ContentClick(slot, region.indexOf(slot), kind, cursor, current);
        if (!region.editable()) {
            // A read-only region paints the feature's own record, so the click is a request it carries out there.
            provider.clicked(holder.ctx(), region, click);
            return;
        }
        if (provider.allows(holder.ctx(), region, click)) {
            event.setCancelled(false);
        }
    }

    /**
     * Move a shift-clicked stack from the viewer's own inventory into the first free slot that accepts it, in the
     * first editable region that has one. The event stays cancelled and the two inventories are written here instead,
     * so the stack lands in the region rather than wherever vanilla would have spread it. When nothing accepts it,
     * nothing moves.
     *
     * <p>A refusal skips that one slot rather than the whole region, because {@link ContentProvider#allows} is asked
     * per movement everywhere else in this router: a provider that reserves its first slot and takes the rest is
     * saying something the contract lets it say, and asking only once would silently turn it into a refusal of the
     * region. The provider is asked at most once per free slot, which is bounded by the region's own size.
     */
    private void handleContentShiftInsert(MenuHolder holder, InventoryClickEvent event, int topSize) {
        ItemStack moved = emptyToNull(event.getCurrentItem());
        if (moved == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        for (ContentRegionSpec region : holder.spec().contents().values()) {
            ContentProvider provider = contentProvider(region);
            if (!region.editable() || provider == null) {
                continue;
            }
            for (int index = 0; index < region.slots().slots().size(); index++) {
                int slot = region.slots().slots().get(index);
                if (slot >= topSize || emptyToNull(top.getItem(slot)) != null) {
                    continue;
                }
                ContentClick click = new ContentClick(slot, index, ContentClick.Kind.INSERT, moved, null);
                if (!provider.allows(holder.ctx(), region, click)) {
                    continue;
                }
                top.setItem(slot, moved.clone());
                event.setCurrentItem(null);
                return;
            }
        }
    }

    /**
     * Whether a drag may go through: every top slot it touches must lie in an editable region whose provider accepts
     * the stack that would land there. A drag over anything else (chrome, a read-only region, a menu with no region
     * at all) is refused by the caller, which is the behaviour every menu had before content regions existed.
     */
    boolean dragAllowed(MenuHolder holder, InventoryDragEvent event) {
        if (holder.spec().contents().isEmpty() || !(event.getWhoClicked() instanceof Player)) {
            return false;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int slot = entry.getKey();
            if (slot >= topSize) {
                continue;
            }
            ContentRegionSpec region = holder.spec().regionAt(slot).orElse(null);
            ContentProvider provider = region == null ? null : contentProvider(region);
            if (region == null || !region.editable() || provider == null) {
                return false;
            }
            ContentClick click = new ContentClick(
                    slot,
                    region.indexOf(slot),
                    ContentClick.Kind.INSERT,
                    entry.getValue(),
                    emptyToNull(event.getView().getTopInventory().getItem(slot)));
            if (!provider.allows(holder.ctx(), region, click)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hand each region's final contents back to its provider as the window closes, in the region's declared slot
     * order. This is the feature's one chance to return or persist what is left in a region it let the viewer fill;
     * the window is discarded right after. A menu with no region, or one whose providers are not registered, does
     * nothing here.
     */
    void readBack(MenuHolder holder, Inventory inventory) {
        if (holder.spec().contents().isEmpty()) {
            return;
        }
        for (ContentRegionSpec region : holder.spec().contents().values()) {
            ContentProvider provider = contentProvider(region);
            if (provider == null) {
                continue;
            }
            List<@Nullable ItemStack> stacks = new ArrayList<>();
            for (int slot : region.slots().slots()) {
                stacks.add(slot < inventory.getSize() ? emptyToNull(inventory.getItem(slot)) : null);
            }
            provider.readBack(holder.ctx(), region, stacks);
        }
    }

    /** The provider registered for {@code region}, or null when the engine has none (a spec-only test engine). */
    @Nullable private ContentProvider contentProvider(ContentRegionSpec region) {
        return contents == null ? null : contents.get(region.id()).orElse(null);
    }

    /**
     * Whether the gesture reaches beyond the one slot the provider is being asked about, and so can never be let
     * through: a double-click gathers matching stacks from the whole window, and a hotbar swap or an off-hand swap
     * pulls from a slot outside the region.
     */
    private static boolean refusedGesture(InventoryClickEvent event) {
        return event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.UNKNOWN;
    }

    /** {@code null} for an absent or air stack, so "empty" is one condition everywhere in the content routing. */
    @Nullable private static ItemStack emptyToNull(@Nullable ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR ? null : stack;
    }
}
