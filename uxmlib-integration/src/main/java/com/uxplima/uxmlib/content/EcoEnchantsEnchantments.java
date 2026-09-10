package com.uxplima.uxmlib.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@link ForeignEnchantments} over EcoEnchants.
 *
 * <p>Read by shape rather than by API, and deliberately. EcoEnchants is built on libreforge, which this
 * estate may not use: its licence forbids production use until a change date, and reading its API from a
 * jar we ship is exactly the use it forbids. What is read instead is the item, which is ours to read: an
 * enchantment plugin leaves its levels in the item's own container, and any integer under any key there is
 * offered under that key.
 *
 * <p>That makes this provider wider than its name. A server running a fourth enchantment plugin nobody here
 * has heard of gets the same answers, because nothing about the shape is EcoEnchants specific. The name is
 * on the class because EcoEnchants is what the analysis asked for and what an operator will look for.
 */
public final class EcoEnchantsEnchantments implements ForeignEnchantments {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "EcoEnchants";

    /** The namespace an id read this way carries, so it is never confused with one of our own. */
    static final String NAMESPACE = "ecoenchants";

    private final Server server;

    public EcoEnchantsEnchantments(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean active() {
        return Reflected.enabled(server, PLUGIN);
    }

    @Override
    public Map<String, Integer> on(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!active() || !stack.hasItemMeta()) {
            return Map.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Map.of();
        }
        Map<String, Integer> found = new HashMap<>();
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            Integer level = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                found.put(NAMESPACE + ":" + key.getKey(), level);
            }
        }
        return Map.copyOf(found);
    }
}
