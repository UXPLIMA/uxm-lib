package com.uxplima.uxmlib.hook.region;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.hook.PluginHook;

/**
 * Every region plugin this server runs, asked together.
 *
 * <p>{@link RegionHooks#active()} answers with the first present provider and nothing else, which is
 * correct for "may this player build here" and wrong for everything else. A server running WorldGuard for
 * its spawn and Towny for its towns has two region models, and asking only the first means a town is
 * invisible to anything that reads a region name: a jobs zone bonus written against a town would never
 * fire, and nothing would say why.
 *
 * <p>So the four questions fold differently, and each fold is the safe one.
 *
 * <ul>
 *   <li>Building and interacting are refused if any provider refuses. Two protection plugins are two
 *       protections, and honouring the more permissive one would let a plugin of ours do what a player
 *       standing there could not.
 *   <li>The regions covering a point are the union. A name is a name whoever gave it.
 *   <li>Wilderness is land no provider claims, which follows from the union being empty.
 * </ul>
 */
public final class CompositeRegionService implements RegionService {

    private final List<RegionService> members;

    public CompositeRegionService(List<RegionService> members) {
        this.members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    /**
     * The names of every member, joined, so a log line about the composite names what is in it.
     *
     * <p>A composite is not a plugin, so there is no one name to give. Naming the members is the honest
     * answer and the useful one: an operator reading "worldguard+towny" knows both were asked.
     */
    @Override
    public String pluginName() {
        return members.stream()
                .map(PluginHook::pluginName)
                .reduce((one, two) -> one + "+" + two)
                .orElse("none");
    }

    @Override
    public boolean isAvailable() {
        return members.stream().anyMatch(RegionService::isAvailable);
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
        for (RegionService member : members) {
            if (member.isAvailable() && !member.canBuild(player, location)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
        for (RegionService member : members) {
            if (member.isAvailable() && !member.canInteract(player, location)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<String> regionsAt(Location location) {
        Objects.requireNonNull(location, "location");
        Set<String> found = new LinkedHashSet<>();
        for (RegionService member : members) {
            if (member.isAvailable()) {
                found.addAll(member.regionsAt(location));
            }
        }
        return Set.copyOf(found);
    }

    @Override
    public boolean isWilderness(Location location) {
        Objects.requireNonNull(location, "location");
        return regionsAt(location).isEmpty();
    }
}
