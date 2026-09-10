package com.uxplima.uxmlib.content;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * CustomHarvests over CustomFishing.
 *
 * <p>CustomFishing gives a catch an id nothing else on the server knows, which is exactly the thing a jobs file wants to price. It grows nothing, so the crop question is left to the plugins that do.
 *
 * <p>Reflective, like every provider in this package.
 */
public final class CustomFishingHarvests implements CustomHarvests {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "CustomFishing";

    /** The namespace an id of this vendor's carries. */
    static final String NAMESPACE = "customfishing";

    /**
     * What this vendor calls the two questions, or empty when it does not answer one of them.
     *
     * <p>A fishing plugin grows nothing and a farming plugin catches nothing. An empty name here is that
     * fact written down, and the method it names is never looked up.
     */
    private static final String CROP_METHOD = "";

    private static final String CATCH_METHOD = "getLootId";

    private static final String API = "net.momirealms.customfishing.api.BukkitCustomFishingPlugin";

    private final Server server;

    public CustomFishingHarvests(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean active() {
        return Reflected.enabled(server, PLUGIN) && Reflected.type(API).isPresent();
    }

    @Override
    public Optional<String> cropAt(Block block) {
        Objects.requireNonNull(block, "block");
        if (!active() || CROP_METHOD.isEmpty()) {
            return Optional.empty();
        }
        return Reflected.method(API, CROP_METHOD, "org.bukkit.block.Block")
                .flatMap(method -> Reflected.callForString(method, null, block))
                .filter(id -> !id.isBlank())
                .map(id -> NAMESPACE + ":" + id);
    }

    @Override
    public Optional<String> catchOf(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!active() || CATCH_METHOD.isEmpty()) {
            return Optional.empty();
        }
        return Reflected.method(API, CATCH_METHOD, "org.bukkit.inventory.ItemStack")
                .flatMap(method -> Reflected.callForString(method, null, stack))
                .filter(id -> !id.isBlank())
                .map(id -> NAMESPACE + ":" + id);
    }
}
