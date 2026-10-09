package com.uxplima.uxmlib.item;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.jspecify.annotations.Nullable;

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
            DataComponentTypes.CAN_PLACE_ON,
            DataComponentTypes.BLOCK_DATA,
            DataComponentTypes.CONTAINER_LOOT,
            DataComponentTypes.DAMAGE,
            DataComponentTypes.FIREWORK_EXPLOSION,
            DataComponentTypes.INTANGIBLE_PROJECTILE,
            DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER,
            DataComponentTypes.POT_DECORATIONS,
            DataComponentTypes.PROFILE,
            DataComponentTypes.TROPICAL_FISH_PATTERN,
            DataComponentTypes.PAINTING_VARIANT,
            DataComponentTypes.SULFUR_CUBE_CONTENT);

    /**
     * The components the client writes a line for that the API names no constant for, by their key. The
     * spawner's "Interact with Spawn Egg" and the mob a spawner holds are the block entity's; a beehive's
     * bees and a spawn egg's entity are the other two.
     */
    static final List<String> VANILLA_COMPONENTS_BY_KEY = List.of("block_entity_data", "entity_data", "bees");

    /**
     * The item a menu draws {@code material} on when it {@link #writesItsOwnLines}: one that writes nothing,
     * wearing {@code material}'s model so the player sees what was asked for.
     */
    public static final Material CARRIER = Material.PAPER;

    /** {@link #vanillaComponents()}, worked out once a server's registries can answer. */
    private static volatile @Nullable Set<DataComponentType> everyVanillaComponent;

    /**
     * Every component the client writes a line for by itself: {@link #VANILLA_COMPONENTS} and those of
     * {@link #VANILLA_COMPONENTS_BY_KEY} the running server knows. A key the registry cannot answer is left
     * out rather than failing a menu, and then asked again next time.
     */
    public static Set<DataComponentType> vanillaComponents() {
        Set<DataComponentType> known = everyVanillaComponent;
        if (known != null) {
            return known;
        }
        Set<DataComponentType> every = new HashSet<>(VANILLA_COMPONENTS);
        boolean complete = true;
        for (String key : VANILLA_COMPONENTS_BY_KEY) {
            DataComponentType found = byKey(key);
            if (found == null) {
                complete = false;
            } else {
                every.add(found);
            }
        }
        Set<DataComponentType> answer = Set.copyOf(every);
        if (complete) {
            everyVanillaComponent = answer;
        }
        return answer;
    }

    private static @Nullable DataComponentType byKey(String key) {
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.DATA_COMPONENT_TYPE)
                    .get(NamespacedKey.minecraft(key));
        } catch (RuntimeException registryUnavailable) {
            return null;
        }
    }

    /**
     * Whether {@code material} writes lines no component hides. A smithing template says what it applies to
     * and what it needs, and a disc fragment names its disc, from the item itself: hiding every component
     * leaves them, and only drawing another item in its look takes them away.
     */
    public static boolean writesItsOwnLines(Material material) {
        Objects.requireNonNull(material, "material");
        return material.name().endsWith("_SMITHING_TEMPLATE") || material == Material.DISC_FRAGMENT_5;
    }

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
