package com.uxplima.uxmlib.content;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * CustomItems over Oraxen.
 *
 * <p>Oraxen keeps its ids on the item itself and answers for one through its own static API. The block question goes through the same call, because Oraxen models a custom block as the item it is placed from.
 *
 * <p>Reflective, like every provider in this package: not one Oraxen type is named in a signature or a
 * field, so not one of its classes loads on a server that does not run it. A version whose API moved
 * answers nothing rather than throwing, and the block break that asked carries on.
 */
public final class OraxenCustomItems implements CustomItems {

    /** The Bukkit plugin name, which is the whole of the present guard. */
    static final String PLUGIN = "Oraxen";

    /** The namespace an id of this vendor's carries, so two vendors' ids can never be confused. */
    static final String NAMESPACE = "oraxen";

    private static final String API = "io.th0rgal.oraxen.api.OraxenItems";

    private final Server server;

    public OraxenCustomItems(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean active() {
        return Reflected.enabled(server, PLUGIN) && Reflected.type(API).isPresent();
    }

    @Override
    public Optional<String> idOf(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!active()) {
            return Optional.empty();
        }
        return Reflected.method(API, "getIdByItem", "org.bukkit.inventory.ItemStack")
                .flatMap(method -> Reflected.callForString(method, null, stack))
                .map(OraxenCustomItems::namespaced);
    }

    @Override
    public Optional<String> idOfBlock(Block block) {
        Objects.requireNonNull(block, "block");
        if (!active()) {
            return Optional.empty();
        }
        return Reflected.method(API, "getIdByItem", "org.bukkit.block.Block")
                .flatMap(method -> Reflected.callForString(method, null, block))
                .map(OraxenCustomItems::namespaced);
    }

    @Override
    public Optional<ItemStack> itemOf(String id) {
        Objects.requireNonNull(id, "id");
        if (!active() || !id.toLowerCase(java.util.Locale.ROOT).startsWith(NAMESPACE + ":")) {
            return Optional.empty();
        }
        String plain = id.substring(NAMESPACE.length() + 1);
        Method builder =
                Reflected.method(API, "getItemById", "java.lang.String").orElse(null);
        if (builder == null) {
            return Optional.empty();
        }
        return Reflected.call(builder, null, plain)
                .map(OraxenCustomItems::asItem)
                .filter(Objects::nonNull);
    }

    /**
     * What the vendor handed back, as an item.
     *
     * <p>Some of them answer with a stack and some with a builder that makes one. Both are tried, because
     * which it is has changed between versions of more than one of these plugins.
     */
    private static @org.jspecify.annotations.Nullable ItemStack asItem(Object answered) {
        if (answered instanceof ItemStack stack) {
            return stack;
        }
        return Reflected.method(answered.getClass().getName(), "build")
                .flatMap(method -> Reflected.call(method, answered))
                .filter(ItemStack.class::isInstance)
                .map(ItemStack.class::cast)
                .orElse(null);
    }

    private static String namespaced(String id) {
        return NAMESPACE + ":" + id;
    }
}
