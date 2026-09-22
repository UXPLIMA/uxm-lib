package com.uxplima.uxmlib.hook.region;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one reflective way into WorldGuard, for every plugin of ours and for this library.
 *
 * <p>WorldGuard is named only by string class name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or method signature here carries a
 * {@code com.sk89q} type and none of its classes load on a server without it. Every caller guards on
 * {@link #isEnabled} or {@link #isInstalled} before it invokes anything else.
 *
 * <p>A reflective chain is exactly the code that must not be copied. The compiler does not check it, so a
 * WorldGuard release that moves one step breaks every copy separately, and the copy somebody forgets is the
 * one that fails on a customer's server. On 2026-09-22 there were two: uxmEssentials held this one, and
 * this library held a smaller one for its claim provider. This is the one left. uxmEssentials wrote it and
 * it moved here, because reaching into another plugin is a mechanism.
 *
 * <p>Every method rethrows its reflective failure rather than swallowing it, so each caller keeps its own
 * fail-open policy: the claim provider degrades to unclaimed, a region gate to "not denied".
 */
@NullMarked
public final class WorldGuardReflection {

    /** The Bukkit plugin name, matching the soft-depend declared in {@code paper-plugin.yml}. */
    public static final String PLUGIN = "WorldGuard";

    private WorldGuardReflection() {}

    /**
     * Whether WorldGuard is installed <em>and</em> enabled: the guard every runtime read uses, because a query
     * against a plugin that failed its own startup would throw rather than answer.
     */
    public static boolean isEnabled(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().isPluginEnabled(PLUGIN);
    }

    /**
     * Whether WorldGuard is installed at all, enabled or not. Flag registration and the wiring probe run during the
     * load phase, when WorldGuard is known to the plugin manager but has not enabled yet: {@link #isEnabled} is
     * false there even though the flag registry is ready, so those callers ask this instead.
     */
    public static boolean isInstalled(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().getPlugin(PLUGIN) != null;
    }

    /** True only when a covering region explicitly sets the named custom flag to DENY at {@code location}. */
    public static boolean queryDeny(Location location, String flagName) throws ReflectiveOperationException {
        Object registry = flagRegistry();
        Object flag = registry.getClass().getMethod("get", String.class).invoke(registry, flagName);
        if (flag == null) {
            return false; // the flag never registered (WorldGuard loaded after us); nothing to enforce
        }
        Object regions = applicableRegions(createQuery(), adapt(location));
        return regions != null && isDeny(queryState(regions, flag));
    }

    /** {@code WorldGuard.getInstance()}: the singleton every reflective walk into WorldGuard starts from. */
    public static Object instance() throws ReflectiveOperationException {
        return Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance")
                .invoke(null);
    }

    /** {@code WorldGuard.getInstance().getPlatform().getRegionContainer()}: the container both queries and CRUD use. */
    public static Object regionContainer() throws ReflectiveOperationException {
        Object instance = instance();
        Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
        return platform.getClass().getMethod("getRegionContainer").invoke(platform);
    }

    /** {@code WorldGuard.getInstance().getFlagRegistry()}: where custom flags are registered and looked up. */
    public static Object flagRegistry() throws ReflectiveOperationException {
        Object instance = instance();
        return instance.getClass().getMethod("getFlagRegistry").invoke(instance);
    }

    /** {@code RegionContainer#createQuery()}: the spatial query object every "what covers this spot" read needs. */
    public static Object createQuery() throws ReflectiveOperationException {
        Object container = regionContainer();
        return container.getClass().getMethod("createQuery").invoke(container);
    }

    /** Adapt a Bukkit {@link Location} to the WorldEdit location the region container's query expects. */
    public static Object adapt(Location location) throws ReflectiveOperationException {
        return Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", Location.class)
                .invoke(null, location);
    }

    /** Adapt a Bukkit {@link World} to the WorldEdit world the region container's per-world lookup expects. */
    public static Object adaptWorld(World world) throws ReflectiveOperationException {
        return Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", World.class)
                .invoke(null, world);
    }

    /** {@code RegionQuery#getApplicableRegions(Location)} matched by the single WorldEdit-location argument. */
    public static @Nullable Object applicableRegions(Object query, Object weLocation)
            throws ReflectiveOperationException {
        for (Method candidate : query.getClass().getMethods()) {
            if (candidate.getName().equals("getApplicableRegions")
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(weLocation)) {
                return candidate.invoke(query, weLocation);
            }
        }
        throw new NoSuchMethodException("getApplicableRegions");
    }

    /** {@code ApplicableRegionSet#queryState(RegionAssociable, StateFlag...)} with a null subject and our one flag. */
    private static @Nullable Object queryState(Object regions, Object flag) throws ReflectiveOperationException {
        Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
        Object flags = Array.newInstance(stateFlag, 1);
        Array.set(flags, 0, flag);
        for (Method candidate : regions.getClass().getMethods()) {
            if (candidate.getName().equals("queryState")
                    && candidate.getParameterCount() == 2
                    && candidate.getParameterTypes()[1].isArray()) {
                return candidate.invoke(regions, null, flags);
            }
        }
        throw new NoSuchMethodException("queryState");
    }

    /** The resolved {@code StateFlag.State} is a DENY only when its enum constant is named {@code DENY}. */
    static boolean isDeny(@Nullable Object state) {
        return state instanceof Enum<?> value && "DENY".equals(value.name());
    }
}
