package com.uxplima.uxmlib.content;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

/**
 * What another plugin's enchantments an item is carrying.
 *
 * <p>None of the competitors reads this either. A server running an enchantment plugin beside ours has
 * items whose enchantments ours cannot see, so a condition written against one of them is a condition that
 * never fires and an operator who wrote it has no way to tell why.
 *
 * <p>The level is what the vendor says it is. Nothing here caps, scales or interprets one: an enchantment
 * another plugin owns is that plugin's to define, and this reads it.
 */
public interface ForeignEnchantments {

    /** A seam that answers for nothing, which is a server running no other enchantment plugin. */
    ForeignEnchantments NONE = new ForeignEnchantments() {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Map<String, Integer> on(ItemStack stack) {
            return Map.of();
        }
    };

    /** Whether any other enchantment plugin answered on this server. */
    boolean active();

    /**
     * Every enchantment of another plugin's this item carries, by that plugin's own name for it.
     *
     * <p>Namespaced by vendor, so an operator writing a condition against one knows which plugin they are
     * naming and two vendors' enchantments of the same name never merge.
     */
    Map<String, Integer> on(ItemStack stack);
}
