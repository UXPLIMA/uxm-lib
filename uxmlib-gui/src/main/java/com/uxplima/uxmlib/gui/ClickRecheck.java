package com.uxplima.uxmlib.gui;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * The anti-desync re-check (item 39). A dynamic, stateful, or animated icon can change between the moment a
 * menu was rendered and the moment a viewer clicks it: the player may be acting on a stale icon. Before a
 * click action runs, the framework confirms the slot still holds the icon the click targeted; if it no
 * longer matches, the action is cancelled and skipped so a click never fires against an icon that is no
 * longer there.
 *
 * <p>The comparison is a pure predicate so it can be unit-tested without a live inventory. It is cheap for
 * us because items already resolve from a {@code RenderContext}; this only compares the captured icon to the
 * one currently in the slot. Pattern from nightcore (re-run visibility at click) / triumph-gui.
 */
final class ClickRecheck {

    private ClickRecheck() {}

    /**
     * Whether the slot still holds the icon the click targeted. {@code clicked} is the icon snapshotted at
     * click time; {@code current} is what is in the slot now ({@code null} = the slot is empty). An empty
     * slot is treated as AIR, so a click on a truly empty slot still matches.
     */
    static boolean stillMatches(ItemStack clicked, @Nullable ItemStack current) {
        Objects.requireNonNull(clicked, "clicked");
        ItemStack live = current == null ? new ItemStack(Material.AIR) : current;
        return clicked.equals(live);
    }
}
