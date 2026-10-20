package com.uxplima.uxmlib.hologram;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/**
 * Tracks the holograms a plugin spawns so they can all be despawned at once. A {@link TextDisplay}
 * hologram is a persistent world entity: if the plugin disables or reloads without removing it, it
 * lingers as an orphan. Spawn through a manager and call {@link #removeAll()} from {@code onDisable}
 * (and before re-spawning on reload) and that cannot happen.
 *
 * <p>The tracked set is concurrent, so spawning and removing from different region threads is safe; the
 * individual {@link Hologram#remove()} and {@link Hologram#setText} calls must still run on each entity's
 * own region thread (Folia), as documented on {@link Hologram}.
 */
public final class HologramManager {

    private final Set<Hologram> tracked = ConcurrentHashMap.newKeySet();
    private final Set<ModelHologram> trackedModels = ConcurrentHashMap.newKeySet();
    private final List<HologramLifecycle> lifecycles = new CopyOnWriteArrayList<>();

    private final String owner;

    /**
     * A manager whose holograms carry no owner. Its {@link #sweepOrphans} removes only leftovers that carry none
     * either, so it never touches another plugin's. Prefer {@link #HologramManager(String)}.
     */
    public HologramManager() {
        this("");
    }

    /**
     * A manager that writes {@code owner}, the plugin's name, on every entity it spawns or tracks. Entities persist
     * through a shutdown or a crash, and {@link #sweepOrphans} then removes this plugin's leftovers and no one
     * else's: several plugins on one server share the world and the marker. Installs the built-in lifecycle that
     * keeps the tracked-hologram viewer caches honest.
     */
    public HologramManager(String owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        registerLifecycle(new ViewerInvalidation());
    }

    /** Spawn {@code builder} at {@code location} and track the result. Must run on the region thread. */
    public Hologram spawn(Holograms.Builder builder, Location location) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(location, "location");
        return track(builder.spawnAt(location));
    }

    /** Track a hologram spawned elsewhere so {@link #removeAll()} will despawn it too. */
    public Hologram track(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (!owner.isEmpty()) {
            Markers.own(hologram.entity(), owner);
        }
        tracked.add(hologram);
        return hologram;
    }

    /** Spawn {@code builder} at {@code location} and track the resulting item/block hologram. */
    public ModelHologram spawn(Holograms.ModelBuilder<?> builder, Location location) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(location, "location");
        return track(builder.spawnAt(location));
    }

    /** Track an item or block hologram spawned elsewhere so {@link #removeAll()} will despawn it too. */
    public ModelHologram track(ModelHologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (!owner.isEmpty()) {
            Markers.own(hologram.entity(), owner);
        }
        trackedModels.add(hologram);
        return hologram;
    }

    /** Despawn one tracked hologram now and stop tracking it. */
    public void remove(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (tracked.remove(hologram)) {
            hologram.remove();
        }
    }

    /** Despawn one tracked item or block hologram now and stop tracking it. */
    public void remove(ModelHologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (trackedModels.remove(hologram)) {
            hologram.remove();
        }
    }

    /** How many holograms are currently tracked (text, item and block together). */
    public int count() {
        return tracked.size() + trackedModels.size();
    }

    /**
     * Install the single Bukkit listener that drives the lifecycle SPI. It forwards every join / quit /
     * respawn / world-change to the registered {@link HologramLifecycle} widgets (and the built-in viewer
     * invalidation), so a consumer wires this once on enable and every widget gets its callbacks for free.
     * Native entities don't need a packet re-send, but our {@code Set<UUID>} viewer caches and any per-player
     * widget state would otherwise hold a departed UUID or stale visibility forever.
     */
    public void installLifecycleListener(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(new HologramLifecycleListener(this), plugin);
    }

    /**
     * Register a widget's {@link HologramLifecycle} so the manager fans player events out to it. A widget
     * (paged / leaderboard / switchable) calls this once when it is created and {@link #unregisterLifecycle}
     * when it is removed, so its per-player state resets centrally without its own Bukkit listener.
     */
    public void registerLifecycle(HologramLifecycle lifecycle) {
        lifecycles.add(Objects.requireNonNull(lifecycle, "lifecycle"));
    }

    /** Stop fanning player events out to {@code lifecycle}. A no-op if it was not registered. */
    public void unregisterLifecycle(HologramLifecycle lifecycle) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        lifecycles.remove(lifecycle);
    }

    /** Fan a join out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchJoin(UUID player) {
        fanOut(player, HologramLifecycle::onJoin);
    }

    /** Fan a quit out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchQuit(UUID player) {
        fanOut(player, HologramLifecycle::onQuit);
    }

    /** Fan a world-change out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchWorldChange(UUID player) {
        fanOut(player, HologramLifecycle::onWorldChange);
    }

    /** Fan a respawn out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchRespawn(UUID player) {
        fanOut(player, HologramLifecycle::onRespawn);
    }

    private void fanOut(UUID player, java.util.function.BiConsumer<HologramLifecycle, UUID> hook) {
        Objects.requireNonNull(player, "player");
        for (HologramLifecycle lifecycle : lifecycles) {
            try {
                hook.accept(lifecycle, player);
            } catch (RuntimeException failure) {
                // One widget's broken hook must never stop the others (or leak the viewer cache); isolate it.
            }
        }
    }

    /** Drop {@code viewer} from every tracked hologram's allowed-viewer set (text, item and block). */
    public void invalidateViewer(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        for (Hologram hologram : tracked) {
            hologram.forgetViewer(viewer);
        }
        for (ModelHologram hologram : trackedModels) {
            hologram.forgetViewer(viewer);
        }
    }

    /**
     * The built-in lifecycle: a quit, respawn or world-change drops the player from every tracked hologram's
     * viewer set so the per-UUID cache neither leaks a departed player nor holds a since-moved one. This is
     * the behaviour the old standalone listener had, now expressed as a registered SPI member.
     */
    private final class ViewerInvalidation implements HologramLifecycle {
        @Override
        public void onQuit(UUID player) {
            invalidateViewer(player);
        }

        @Override
        public void onWorldChange(UUID player) {
            invalidateViewer(player);
        }

        @Override
        public void onRespawn(UUID player) {
            invalidateViewer(player);
        }
    }

    /** Despawn every tracked hologram (text, item and block) and clear the sets. Call from {@code onDisable}. */
    public void removeAll() {
        for (Hologram hologram : tracked) {
            hologram.remove();
        }
        tracked.clear();
        for (ModelHologram hologram : trackedModels) {
            hologram.remove();
        }
        trackedModels.clear();
    }

    /**
     * Remove the holograms this plugin left in {@code world} on an earlier run: the uxmLib-marked
     * {@link org.bukkit.entity.Display} entities carrying this manager's owner that it is not tracking. Display
     * entities persist, so a shutdown that saved the world before they were removed, or a crash, leaves them
     * behind, and each start would add another copy. Run this once the worlds are up and before spawning, on the
     * region or global thread. Another plugin's holograms carry another owner and are left alone. Returns how many
     * were swept.
     */
    public int sweepOrphans(org.bukkit.World world) {
        Objects.requireNonNull(world, "world");
        Set<UUID> live = trackedEntities();
        int swept = 0;
        for (org.bukkit.entity.Display display : world.getEntitiesByClass(org.bukkit.entity.Display.class)) {
            if (isOrphanOf(display, owner, live)) {
                display.remove();
                swept++;
            }
        }
        return swept;
    }

    /**
     * Remove this plugin's leftovers chunk by chunk, as their entities load, from now on. Each chunk is swept on
     * its own thread, which is the only safe way on a server that splits the world into regions, and a chunk that
     * loads late, long after startup, is swept the first time somebody walks into it. Needs an owner: a manager
     * with none cannot tell its leftovers from anybody else's.
     */
    public void sweepLeftoversAsTheyLoad(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (owner.isEmpty()) {
            throw new IllegalStateException("a manager with no owner cannot tell its leftovers from another plugin's");
        }
        plugin.getServer().getPluginManager().registerEvents(new LeftoverSweep(this), plugin);
    }

    /** The listener behind {@link #sweepLeftoversAsTheyLoad}: one chunk's entities, as they load. */
    static final class LeftoverSweep implements org.bukkit.event.Listener {

        private final HologramManager manager;

        LeftoverSweep(HologramManager manager) {
            this.manager = manager;
        }

        @org.bukkit.event.EventHandler
        public void onLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
            manager.sweepIfLeftover(event.getEntities());
        }
    }

    /** Remove each of {@code loaded} that is a leftover of this manager's owner. */
    void sweepIfLeftover(List<? extends org.bukkit.entity.Entity> loaded) {
        Set<UUID> live = trackedEntities();
        for (org.bukkit.entity.Entity entity : loaded) {
            if (isOrphanOf(entity, owner, live)) {
                entity.remove();
            }
        }
    }

    /** Whether {@code entity} is a hologram {@code owner} left behind: marked, owned by it, and not live. */
    static boolean isOrphanOf(org.bukkit.entity.Entity entity, String owner, Set<UUID> live) {
        return Markers.isHologram(entity)
                && Markers.ownerOf(entity).equals(owner)
                && !live.contains(entity.getUniqueId());
    }

    private Set<UUID> trackedEntities() {
        Set<UUID> live = new java.util.HashSet<>();
        tracked.forEach(hologram -> live.add(hologram.entity().getUniqueId()));
        trackedModels.forEach(hologram -> live.add(hologram.entity().getUniqueId()));
        return live;
    }
}
