package com.uxplima.uxmlib.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Every custom item plugin this server runs, asked in turn.
 *
 * <p>A server may run two of them, and often does: one for the resource pack items and one for the blocks.
 * The first that answers wins, and the order is the order the registry lists them, which is stable and
 * written down rather than being whatever the plugin manager happened to return.
 *
 * <p>An id is namespaced by its vendor, so asking every vendor for one id is cheap and unambiguous: only
 * the vendor whose namespace it carries can answer.
 */
public final class CompositeCustomItems implements CustomItems {

    private final List<CustomItems> members;

    public CompositeCustomItems(List<CustomItems> members) {
        this.members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    @Override
    public boolean active() {
        return members.stream().anyMatch(CustomItems::active);
    }

    @Override
    public Optional<String> idOf(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return first(member -> member.idOf(stack));
    }

    @Override
    public Optional<String> idOfBlock(Block block) {
        Objects.requireNonNull(block, "block");
        return first(member -> member.idOfBlock(block));
    }

    @Override
    public Optional<ItemStack> itemOf(String id) {
        Objects.requireNonNull(id, "id");
        for (CustomItems member : members) {
            Optional<ItemStack> found = member.itemOf(id);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<String> first(java.util.function.Function<CustomItems, Optional<String>> question) {
        for (CustomItems member : members) {
            Optional<String> answer = question.apply(member);
            if (answer.isPresent()) {
                return answer;
            }
        }
        return Optional.empty();
    }
}
