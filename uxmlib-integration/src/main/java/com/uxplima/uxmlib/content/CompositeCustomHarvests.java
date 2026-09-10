package com.uxplima.uxmlib.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** Every custom crop or fishing plugin this server runs, asked in turn. The first that answers wins. */
public final class CompositeCustomHarvests implements CustomHarvests {

    private final List<CustomHarvests> members;

    public CompositeCustomHarvests(List<CustomHarvests> members) {
        this.members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    @Override
    public boolean active() {
        return members.stream().anyMatch(CustomHarvests::active);
    }

    @Override
    public Optional<String> cropAt(Block block) {
        Objects.requireNonNull(block, "block");
        return first(member -> member.cropAt(block));
    }

    @Override
    public Optional<String> catchOf(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return first(member -> member.catchOf(stack));
    }

    private Optional<String> first(Function<CustomHarvests, Optional<String>> question) {
        for (CustomHarvests member : members) {
            Optional<String> answer = question.apply(member);
            if (answer.isPresent()) {
                return answer;
            }
        }
        return Optional.empty();
    }
}
