package com.uxplima.uxmlib.condition.currency;

import java.util.Optional;

import org.bukkit.Material;

/**
 * The material under a name.
 *
 * <p>It is a seam of one method so that reading {@code currencies.conf} can be tested without a server:
 * the server answers this by asking its own registry, and a test answers it with the enum constant it
 * means.
 */
public interface Items {

    Optional<Material> byName(String name);

    /** The server. */
    static Items ofServer() {
        return name -> Optional.ofNullable(Material.matchMaterial(name));
    }
}
