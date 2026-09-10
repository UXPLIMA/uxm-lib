package com.uxplima.uxmlib.content;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * What a file calls the thing that was just worked on.
 *
 * <p>A plugin of ours prices work by naming what it was aimed at, and without this the name is always the
 * vanilla material or entity type. A server running Oraxen then prices its ruby ore as deepslate, a server
 * running CustomCrops pays the same for a tomato as for the wheat it is drawn as, and a server running a
 * stacker pays for one of the forty mobs a player just killed.
 *
 * <p><strong>Static, and set once.</strong> It is a property of the server rather than of a plugin: five of
 * ours name blocks, items and mobs in files, and several of them do it from a static helper that no
 * constructor reaches. Threading a seam through every one of those would be five copies of the same wiring
 * and a sixth that got forgotten. The placeholder hook and the item source are static for the same reason.
 *
 * <p>Nothing is installed until something installs it, and then every name is the vanilla one. A server
 * running none of these plugins therefore reads exactly what it read before this existed.
 *
 * <p>The order is deliberate. A crop is asked before a block, because a custom crop is drawn as a block of
 * something else and the block question would answer with the drawing. A catch is asked before an item for
 * the same reason.
 */
public final class ContentNames {

    private static volatile CustomItems items = CustomItems.NONE;

    private static volatile CustomMobs mobs = CustomMobs.NONE;

    private static volatile CustomHarvests harvests = CustomHarvests.NONE;

    private ContentNames() {}

    /**
     * Say where a custom name comes from.
     *
     * <p>Called once at enable by whichever plugin of ours starts first, and safe for all of them to call:
     * the seams are the same either way. Calling it again replaces them, which is what a reload does.
     */
    public static void namedBy(CustomItems byItems, CustomMobs byMobs, CustomHarvests byHarvests) {
        items = Objects.requireNonNull(byItems, "byItems");
        mobs = Objects.requireNonNull(byMobs, "byMobs");
        harvests = Objects.requireNonNull(byHarvests, "byHarvests");
    }

    /** Forget them, which a shutdown does so a reload does not hold the old server's plugins. */
    public static void forgetEverything() {
        items = CustomItems.NONE;
        mobs = CustomMobs.NONE;
        harvests = CustomHarvests.NONE;
    }

    /** Whether anything at all answered on this server, which a start line says once. */
    public static boolean active() {
        return items.active() || mobs.active() || harvests.active();
    }

    /** What a file calls this block. */
    public static String of(Block block) {
        Objects.requireNonNull(block, "block");
        return harvests.cropAt(block).or(() -> items.idOfBlock(block)).orElseGet(() -> plain(block.getType()));
    }

    /** What a file calls this item. */
    public static String of(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return harvests.catchOf(stack).or(() -> items.idOf(stack)).orElseGet(() -> plain(stack.getType()));
    }

    /** What a file calls this creature. */
    public static String of(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return mobs.idOf(entity).orElseGet(() -> entity.getType().name().toLowerCase(Locale.ROOT));
    }

    /**
     * How many creatures this one entity stands for.
     *
     * <p>One on a server with no stacker. A caller that ignores it pays a fortieth of what the player
     * earned, which is why the question is here rather than left to each listener.
     */
    public static int countOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return Math.max(1, mobs.countOf(entity));
    }

    /**
     * The same, upper case, for a table that spells a material the way the server does.
     *
     * <p>Two of our plugins write their tables in upper case and three in lower, which is a difference in
     * the files rather than one anybody should have to think about here. A custom id keeps the vendor's own
     * spelling either way: an id is a name and not a material.
     */
    public static String upperOf(Block block) {
        Objects.requireNonNull(block, "block");
        return harvests.cropAt(block)
                .or(() -> items.idOfBlock(block))
                .orElseGet(() -> block.getType().name());
    }

    private static String plain(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }
}
