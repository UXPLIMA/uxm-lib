package com.uxplima.uxmlib.item;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.persistence.PersistentDataContainerView;

/**
 * Marks an item, via its persistent data, as forbidding a set of {@link ItemAction interactions} (crafting,
 * consuming, placing, dropping). The flag is one PDC string under {@link #KEY} holding the comma-joined stable
 * ids of the blocked actions; {@link ItemActionListener} reads it on the relevant events and cancels them.
 *
 * <p>Everything here is native PDC and self-contained: write the set with {@link #block(ItemStack, Set)},
 * read it with {@link #blockedActions(PersistentDataContainerView)}, and test "is this action blocked for this
 * item" with {@link #isBlocked(PersistentDataContainerView, ItemAction)}: all pure given an item view, so the
 * listener stays a thin event-to-predicate bridge.
 */
public final class ItemBlockerType {

    /** The persistent-data key the blocked-action set is stored under. */
    public static final NamespacedKey KEY = new NamespacedKey("uxmlib", "blocked_actions");

    private ItemBlockerType() {}

    /** Mark {@code item} as blocking exactly {@code actions} (an empty set clears the flag). */
    public static void block(ItemStack item, Set<ItemAction> actions) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(actions, "actions");
        Items.editPdc(item, pdc -> write(pdc, actions));
    }

    /** Mark {@code item} as blocking these {@code actions}. */
    public static void block(ItemStack item, ItemAction... actions) {
        Objects.requireNonNull(actions, "actions");
        block(item, toSet(actions));
    }

    // Visible to the builder/listener so the write goes through one place; clears the key on an empty set.
    static void write(PersistentDataContainer pdc, Set<ItemAction> actions) {
        if (actions.isEmpty()) {
            pdc.remove(KEY);
            return;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (ItemAction action : actions) {
            joiner.add(action.id());
        }
        pdc.set(KEY, PersistentDataType.STRING, joiner.toString());
    }

    /** The set of actions blocked for the item behind {@code view}; empty when the item carries no flag. */
    public static Set<ItemAction> blockedActions(PersistentDataContainerView view) {
        Objects.requireNonNull(view, "view");
        String raw = view.get(KEY, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return EnumSet.noneOf(ItemAction.class);
        }
        Set<ItemAction> actions = EnumSet.noneOf(ItemAction.class);
        // -1 limit keeps the split form Error Prone's StringSplitter accepts; empty segments resolve to no action.
        for (String id : raw.split(",", -1)) {
            ItemAction.byId(id).ifPresent(actions::add);
        }
        return actions;
    }

    /** Whether {@code action} is blocked for the item behind {@code view}. */
    public static boolean isBlocked(PersistentDataContainerView view, ItemAction action) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(action, "action");
        return blockedActions(view).contains(action);
    }

    /** Whether {@code item} (its current meta) blocks {@code action}; {@code null} or empty items never do. */
    public static boolean isBlocked(@org.jspecify.annotations.Nullable ItemStack item, ItemAction action) {
        Objects.requireNonNull(action, "action");
        if (item == null || item.isEmpty()) {
            return false;
        }
        return isBlocked(item.getPersistentDataContainer(), action);
    }

    private static Set<ItemAction> toSet(ItemAction... actions) {
        Set<ItemAction> set = EnumSet.noneOf(ItemAction.class);
        set.addAll(Arrays.asList(actions));
        return set;
    }
}
