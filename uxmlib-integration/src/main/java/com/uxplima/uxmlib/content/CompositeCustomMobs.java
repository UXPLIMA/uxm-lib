package com.uxplima.uxmlib.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Entity;

/**
 * Every custom mob plugin this server runs, asked in turn.
 *
 * <p>The two questions fold differently and the difference matters. An id is the first answer, because a mob
 * is one vendor's or another's and never both. A count is the largest answer, because a mob that one plugin
 * says is one and another says is forty is forty: a stacker knows something the mob plugin does not, and
 * taking the smaller number would pay a player for one of the forty they killed.
 */
public final class CompositeCustomMobs implements CustomMobs {

    private final List<CustomMobs> members;

    public CompositeCustomMobs(List<CustomMobs> members) {
        this.members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    @Override
    public boolean active() {
        return members.stream().anyMatch(CustomMobs::active);
    }

    @Override
    public Optional<String> idOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        for (CustomMobs member : members) {
            Optional<String> found = member.idOf(entity);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public int countOf(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        int most = 1;
        for (CustomMobs member : members) {
            most = Math.max(most, member.countOf(entity));
        }
        return most;
    }
}
