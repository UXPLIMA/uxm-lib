package com.uxplima.uxmlib.menu.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole menu parsed from its HOCON spec: a title, a row count, its refresh policy, the requirement that gates
 * opening, the actions run on open and close, the items keyed by their spec id, an optional non-chest inventory
 * shape, and the menu's own custom placeholder definitions. Validated up front so the renderer can trust the row
 * count and slot bounds without re-checking.
 *
 * <p>The {@code inventoryType} is carried as a plain operator token: {@code "hopper"}, {@code "dispenser"}, and so
 * on, never a Bukkit {@code InventoryType}, so this model stays pure and plain-JUnit testable. Absent, the menu is
 * the default {@code rows}-based chest. When present, the Bukkit-side façade maps the token to a real inventory
 * shape and falls back to a {@code rows}-based chest if that shape rejects a custom window, which is why {@code rows}
 * and its {@code 1..6} bound are still validated even for a non-chest menu: they size that fallback.
 *
 * <p>The {@code placeholders} map is the menu's own {@code placeholders {}} block: {@code name -> template} pairs
 * that define custom {@code %name%} tokens scoped to this one menu. It is carried verbatim for the Bukkit-side
 * renderer to expand, and is resolved local-first: a menu-scoped token overrides the shared registry (built-ins,
 * data readers, the global custom-placeholder file) for this menu alone. Empty for a menu that declares no block, so
 * such a menu renders byte-identically to before this field existed.
 *
 * <p>The {@code clickCooldownMs} is the menu's own {@code click-cooldown} anti-spam window in milliseconds: two
 * clicks landing closer together than this are treated as one, so a spam-click can't double-fire an item's actions.
 * A value of {@code 0} means the menu sets no window of its own and defers to the server-wide default, so a menu
 * that declares no key behaves exactly as before.
 *
 * <p>The {@code bottomInventory} flag turns the menu into a 90-slot canvas: beyond the chest top it may also place
 * items into the viewer's own 36 inventory slots, addressed as raw slots {@code rows*9 .. rows*9+35}. The Bukkit-side
 * runtime snapshots and restores the player inventory around such a menu; here it only widens the slot-fit bound, and
 * a bottom-inventory menu is chest-only by construction (the raw-slot geometry only holds for a chest). A menu that
 * leaves the flag {@code false}, the default, validates and renders exactly as before.
 *
 * <p>The {@code chestOnly} flag keeps the menu on the chest render path even for a Bedrock viewer the hybrid form
 * renderer would otherwise redirect. A form is a flat button list, so a menu that displays or edits real item stacks
 * (the inventory viewer, a storage-style grid) cannot be represented as one; setting this flag opts such a menu out
 * of the redirect so its Bedrock viewers still get the chest. It is purely a routing hint here: a plain boolean the
 * Bukkit-side façade reads, so this model stays pure and plain-JUnit testable. A menu that leaves it {@code false},
 * the default: is redirected to a form for a Bedrock viewer and renders exactly as before for a Java one.
 *
 * <p>The {@code contents} map is the menu's {@code content {}} block: the slot regions the engine hands to a
 * feature's {@code ContentProvider} instead of drawing itself, keyed by the provider id each region names. Empty for
 * every menu that declares no block, which is every menu drawn entirely from its own items, so such a menu renders
 * and routes clicks exactly as before this block existed.
 *
 * <p>The {@code bedrock} block is the menu's optional {@code bedrock {}} native CustomForm: when present, a Bedrock
 * viewer opening the menu (and not opted out by {@code chestOnly}) gets that explicit form: the form-native
 * dropdown/slider/toggle/multi-input widgets the automatic SimpleForm degradation cannot express: instead of the
 * degraded button list. It is {@link Optional#empty()} for a menu that declares no block, in which case the automatic
 * degradation is unchanged; a Java viewer never sees it either way. Purely spec here: a value the Bukkit-side façade
 * reads, so this model stays pure and plain-JUnit testable.
 */
public record MenuSpec(
        String title,
        int rows,
        RefreshSpec refresh,
        List<Ref> openRequirement,
        List<Ref> openActions,
        List<Ref> closeActions,
        Map<String, MenuItemSpec> items,
        Optional<String> inventoryType,
        Map<String, String> placeholders,
        long clickCooldownMs,
        boolean bottomInventory,
        boolean chestOnly,
        Optional<BedrockFormSpec> bedrock,
        Map<String, ContentRegionSpec> contents) {

    public MenuSpec {
        Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be in 1..6: " + rows);
        }
        Objects.requireNonNull(refresh, "refresh");
        openRequirement = List.copyOf(Objects.requireNonNull(openRequirement, "openRequirement"));
        openActions = List.copyOf(Objects.requireNonNull(openActions, "openActions"));
        closeActions = List.copyOf(Objects.requireNonNull(closeActions, "closeActions"));
        items = Map.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(inventoryType, "inventoryType");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
        if (clickCooldownMs < 0) {
            throw new IllegalArgumentException("clickCooldownMs must be >= 0: " + clickCooldownMs);
        }
        Objects.requireNonNull(bedrock, "bedrock");
        contents = Map.copyOf(Objects.requireNonNull(contents, "contents"));
        checkSlotsFit(items, rows, bottomInventory);
        checkListsDoNotShareASlot(items);
        checkRegionsFit(contents, rows, bottomInventory);
    }

    /**
     * Whether a Bedrock viewer must get the chest rather than a form. The operator says so with {@code chestOnly}, and
     * two shapes say so without being asked: a {@code content {}} region holds real item stacks and a bottom-inventory
     * menu paints into the viewer's own slots, and a form can show neither. A trade window turned into a form kept
     * its buttons and lost the items it was opened to trade.
     */
    public boolean keepsTheChest() {
        return chestOnly || bottomInventory || !contents.isEmpty();
    }

    /**
     * The list-backed item the page controls drive: the arrows, the Bedrock form's page buttons, and the {@code
     * %page%}/{@code %max_page%} indicator all read this one. It is the list drawn nearest the start of the window,
     * which is the one a viewer would name if asked which list "the list" is. Empty for a menu that carries no list.
     * There is no tie to break: two lists sharing a slot is refused where the menu is built, because such a menu
     * cannot draw both of them anyway.
     *
     * <p>The choice is made from the file and not from the item map. These items arrive from the config library in an
     * order that is neither the order they were written in nor stable from one run to the next, so "the first
     * list-backed item" named a different list on different server starts, and the three readers above could disagree
     * with each other inside one render.
     */
    public Optional<MenuItemSpec> pagedListItem() {
        MenuItemSpec chosen = null;
        int chosenSlot = Integer.MAX_VALUE;
        for (MenuItemSpec item : items.values()) {
            if (item.list().isEmpty()) {
                continue;
            }
            int slot = earliestSlot(item);
            if (slot < chosenSlot) {
                chosen = item;
                chosenSlot = slot;
            }
        }
        return Optional.ofNullable(chosen);
    }

    /** The lowest slot {@code item} draws into, or {@link Integer#MAX_VALUE} when it draws into none. */
    private static int earliestSlot(MenuItemSpec item) {
        int lowest = Integer.MAX_VALUE;
        for (int slot : item.slots().slots()) {
            lowest = Math.min(lowest, slot);
        }
        return lowest;
    }

    /** The content region covering {@code slot}, or empty when the slot is ordinary chrome. */
    public Optional<ContentRegionSpec> regionAt(int slot) {
        for (ContentRegionSpec region : contents.values()) {
            if (region.covers(slot)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /**
     * A plain chest window: its title, rows, refresh, open gate, open and close actions and items, with nothing else
     * set. This is the window the engine draws for its own lists, confirms, selectors and editors.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items) {
        this(
                title,
                rows,
                refresh,
                openRequirement,
                openActions,
                closeActions,
                items,
                Optional.empty(),
                Map.of(),
                0L,
                false,
                false,
                Optional.empty(),
                Map.of());
    }

    /**
     * The id the {@code fill-item} block is stored under, in a file the loader read and in one the in-game editor
     * wrote back. The renderer paints this item into every slot of the window nothing else drew.
     */
    public static final String FILL_ITEM_ID = "__fill__";

    /**
     * The count of the viewer's own inventory slots a bottom-inventory menu may additionally paint into: the 27
     * main slots plus the 9 hotbar slots shown below the chest top.
     */
    private static final int BOTTOM_SLOTS = 36;

    private static void checkRegionsFit(Map<String, ContentRegionSpec> contents, int rows, boolean bottomInventory) {
        int capacity = rows * 9 + (bottomInventory ? BOTTOM_SLOTS : 0);
        for (ContentRegionSpec region : contents.values()) {
            for (int slot : region.slots().slots()) {
                if (slot >= capacity) {
                    throw new IllegalArgumentException(
                            "content region '" + region.id() + "' slot " + slot + " exceeds capacity " + capacity);
                }
            }
        }
    }

    /**
     * Refuse a menu whose lists draw into the same slot. Two list-backed items over one cell cannot both be seen: the
     * one drawn second paints over the first, and which one that is comes from the item map's order rather than from
     * the file. Refusing the menu where it is built turns a window that silently showed the wrong half of itself into
     * a named error the operator can act on, and it is what lets {@link #pagedListItem()} choose on the slot alone.
     */
    private static void checkListsDoNotShareASlot(Map<String, MenuItemSpec> items) {
        Map<Integer, String> claimed = new java.util.HashMap<>();
        List<String> ids = new java.util.ArrayList<>(items.keySet());
        java.util.Collections.sort(ids);
        for (String id : ids) {
            MenuItemSpec item = items.get(id);
            if (item == null || item.list().isEmpty()) {
                continue;
            }
            for (int slot : item.slots().slots()) {
                String other = claimed.putIfAbsent(slot, id);
                if (other != null) {
                    throw new IllegalArgumentException(
                            "items '" + other + "' and '" + id + "' both draw a list into slot " + slot);
                }
            }
        }
    }

    private static void checkSlotsFit(Map<String, MenuItemSpec> items, int rows, boolean bottomInventory) {
        int capacity = rows * 9 + (bottomInventory ? BOTTOM_SLOTS : 0);
        for (MenuItemSpec item : items.values()) {
            for (int slot : item.slots().slots()) {
                if (slot >= capacity) {
                    throw new IllegalArgumentException("slot " + slot + " exceeds capacity " + capacity);
                }
            }
        }
    }
}
