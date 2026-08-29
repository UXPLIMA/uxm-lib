package com.uxplima.uxmlib.item;

import java.util.Objects;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

/**
 * What the client is allowed to say about an item on its own.
 *
 * <p>A menu icon is a button, and the client does not know that: it reads the item's components and writes
 * its own lines under the lore a plugin wrote: what the thing is worn as, what colour it is dyed, how long
 * a firework flies. {@link ItemBuilder#vanillaTooltip} is the usual way in; this is here for an item that
 * arrived from somewhere else and is about to be shown in a menu.
 */
public final class Tooltips {

    private Tooltips() {}

    /**
     * The lines the client writes by itself: what an item is worn as, what it is dyed, what it repairs,
     * how long it flies. Every one of them is right on an item a player owns and wrong on a button, which
     * is why {@link ItemBuilder#vanillaTooltip} exists and why this is the set it hides.
     *
     * <p>The glint is deliberately not in it. A shimmer is a thing a menu says on purpose: this is
     * selected, this is owned, and hiding it would take that away.
     */
    public static final Set<DataComponentType> VANILLA_COMPONENTS = Set.of(
            DataComponentTypes.ATTRIBUTE_MODIFIERS,
            DataComponentTypes.ENCHANTMENTS,
            DataComponentTypes.STORED_ENCHANTMENTS,
            DataComponentTypes.UNBREAKABLE,
            DataComponentTypes.DYED_COLOR,
            DataComponentTypes.TRIM,
            DataComponentTypes.BANNER_PATTERNS,
            DataComponentTypes.FIREWORKS,
            DataComponentTypes.POTION_CONTENTS,
            DataComponentTypes.SUSPICIOUS_STEW_EFFECTS,
            DataComponentTypes.CHARGED_PROJECTILES,
            DataComponentTypes.WRITTEN_BOOK_CONTENT,
            DataComponentTypes.BUNDLE_CONTENTS,
            DataComponentTypes.CONTAINER,
            DataComponentTypes.INSTRUMENT,
            DataComponentTypes.JUKEBOX_PLAYABLE,
            DataComponentTypes.MAP_ID,
            DataComponentTypes.EQUIPPABLE,
            DataComponentTypes.GLIDER,
            DataComponentTypes.BLOCKS_ATTACKS,
            DataComponentTypes.DAMAGE_RESISTANT,
            DataComponentTypes.TOOL,
            DataComponentTypes.WEAPON,
            DataComponentTypes.CAN_BREAK,
            DataComponentTypes.CAN_PLACE_ON);

    /**
     * Hide exactly {@code hidden} on {@code item} and nothing else, replacing whatever was hidden before.
     * A tooltip that was hidden whole stays hidden whole: that is a separate flag on the same component,
     * and silencing the client's lines is no reason to bring a tooltip back that a caller took away.
     */
    public static void hide(ItemStack item, Set<DataComponentType> hidden) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(hidden, "hidden");
        TooltipDisplay current = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        item.setData(
                DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay()
                        .hideTooltip(current != null && current.hideTooltip())
                        .hiddenComponents(Set.copyOf(hidden)));
    }
}
