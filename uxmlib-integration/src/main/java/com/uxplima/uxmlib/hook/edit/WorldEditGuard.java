package com.uxplima.uxmlib.hook.edit;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.uxplima.uxmlib.hook.Hooks;
import org.jspecify.annotations.Nullable;

/**
 * A boundary over WorldEdit, and over every editor that answers WorldEdit's API.
 *
 * <p>An edit does not go through a Bukkit event. Nothing a server plugin can listen for sees a {@code
 * //set}, which is why a plugin that guards its claims with block listeners alone is a plugin one command
 * walks straight through. WorldEdit offers the seam instead: it announces each edit before it runs and lets
 * a listener wrap the thing the edit writes into. That is what this does.
 *
 * <p>FastAsyncWorldEdit is the same API under a different plugin name, so both names are looked for and one
 * implementation covers them.
 *
 * <p>The {@code com.sk89q} classes are named only past the presence guard in {@link #find()}, the same way
 * the WorldGuard seam does it, so a server without an editor never links them.
 *
 * <p>The wrap happens at {@code BEFORE_CHANGE}, which is the last stage before blocks are written and the
 * one every other stage passes through. Wrapping earlier would guard a stage a later one could rewrite.
 */
public final class WorldEditGuard implements EditGuard {

    private static final String WORLD_EDIT = WorldEditNames.WORLD_EDIT;

    private static final String FAST_ASYNC = WorldEditNames.FAST_ASYNC;

    /** The rule in force, or nothing when none has been installed. Read on the editor's own thread. */
    private final AtomicReference<@Nullable EditBoundary> boundary = new AtomicReference<>();

    private final String pluginName;

    /** Whether the handler is on the bus, so a second install does not put it on twice. */
    private final AtomicBoolean listening = new AtomicBoolean();

    // Package-private so the presence guard in find() stays the only public way in, while a test in this
    // package can still exercise the parts that return before any WorldEdit call.
    WorldEditGuard(String pluginName) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
    }

    /**
     * The guard for whichever editor is installed, or nothing when none is.
     *
     * <p>Call {@link EditGuard#forServer()} rather than this. Running this method links this class, which loads
     * WorldEdit's types, so on a server without WorldEdit it throws before its own check can run.
     *
     * <p>FastAsyncWorldEdit is asked about first, and it gets a guard of its own. It answers to the name WorldEdit
     * as well, and it throws away the extent this class hands it, so a WorldEdit guard on a FAWE server guards
     * nothing while every doctor line says it does.
     */
    public static Optional<EditGuard> find() {
        if (Hooks.isPresent(FAST_ASYNC)) {
            return faweGuard();
        }
        return Hooks.isPresent(WORLD_EDIT) ? Optional.of(new WorldEditGuard(WORLD_EDIT)) : Optional.empty();
    }

    /**
     * The FAWE guard, built by name from the half of this module that compiles against FAWE.
     *
     * <p>By name, because this half cannot name a FAWE type and still link on a server without FAWE. The name is
     * read off this class's own package, so it survives a consumer relocating the library. A FAWE whose API this
     * build no longer links against gets no guard rather than one that guards nothing, and the console says so.
     */
    static Optional<EditGuard> faweGuard() {
        String name = WorldEditGuard.class.getPackageName() + ".FaweEditGuard";
        try {
            Class<?> type = Class.forName(name, true, WorldEditGuard.class.getClassLoader());
            return Optional.of((EditGuard) type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            System.getLogger(WorldEditGuard.class.getName())
                    .log(
                            System.Logger.Level.WARNING,
                            "FastAsyncWorldEdit is installed but its edit guard could not be built, so edits are"
                                    + " not bounded: " + e);
            return Optional.empty();
        }
    }

    @Override
    public String pluginName() {
        return pluginName;
    }

    @Override
    public boolean isAvailable() {
        return Hooks.isPresent(pluginName);
    }

    @Override
    public void install(EditBoundary rule) {
        Objects.requireNonNull(rule, "rule");
        boundary.set(rule);
        if (listening.compareAndSet(false, true)) {
            WorldEdit.getInstance().getEventBus().register(this);
        }
    }

    @Override
    public void uninstall() {
        boundary.set(null);
        if (listening.compareAndSet(true, false)) {
            WorldEdit.getInstance().getEventBus().unregister(this);
        }
    }

    /**
     * Wrap what this edit writes into, so every block it sets is asked about first.
     *
     * <p>An edit with no actor is a console edit or another plugin's, and neither is a player whose rights
     * this seam could ask about. It is left alone rather than refused: a plugin guarding a player's
     * boundary must not be the reason an operator's own command stops working.
     */
    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        EditBoundary rule = boundary.get();
        if (rule == null || event.getStage() != EditSession.Stage.BEFORE_CHANGE) {
            return;
        }
        Actor actor = event.getActor();
        World world = event.getWorld();
        if (actor == null || world == null || !actor.isPlayer()) {
            return;
        }
        event.setExtent(new Bounded(event.getExtent(), new Editing(actor, world.getName()), rule));
    }

    /**
     * Who is editing, answered by the editor itself.
     *
     * <p>The permission question goes to the actor and not to the server. The editor holds the answer
     * already and can give it from its own thread, which is the thread this is asked on.
     */
    record Editing(Actor actor, String world) implements EditBoundary.Edit {

        @Override
        public UUID player() {
            return actor.getUniqueId();
        }

        @Override
        public boolean holds(String permission) {
            return actor.hasPermission(permission);
        }
    }

    /**
     * The thing an edit writes into, with a question in front of every write.
     *
     * <p>A refused block is answered {@code false} rather than thrown over. A throw would abort the whole
     * edit at whatever block it reached, leaving half of it applied and no way to say which half; answering
     * no leaves everything inside the boundary written and everything outside it untouched, which is what
     * a player asked for and the only outcome they can reason about.
     */
    static final class Bounded extends AbstractDelegateExtent {

        private final EditBoundary.Edit edit;
        private final EditBoundary rule;

        /** Whether this edit has already been cut short, so the consumer is told once and not per block. */
        private final AtomicBoolean told = new AtomicBoolean();

        Bounded(Extent extent, EditBoundary.Edit edit, EditBoundary rule) {
            super(extent);
            this.edit = edit;
            this.rule = rule;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 at, T block) throws WorldEditException {
            if (cutShort(rule.mayWrite(
                    edit, at.x(), at.y(), at.z(), block.getBlockType().id()))) {
                return false;
            }
            return super.setBlock(at, block);
        }

        @Override
        public boolean setBiome(BlockVector3 at, BiomeType biome) {
            // A biome is a change to the world at a position, so it is the same question. An edit that
            // could repaint the biome of the plot next door has left the boundary just as surely.
            return !cutShort(rule.mayChange(edit, at.x(), at.y(), at.z())) && super.setBiome(at, biome);
        }

        @Override
        public @Nullable Entity createEntity(Location at, BaseEntity entity) {
            BlockVector3 block = BlockVector3.at(at.getX(), at.getY(), at.getZ());
            if (cutShort(rule.mayCreate(
                    edit, block.x(), block.y(), block.z(), entity.getType().id()))) {
                return null;
            }
            return super.createEntity(at, entity);
        }

        /**
         * Take the rule's answer, and tell the consumer the first time it is no.
         *
         * <p>The short accessors and not {@code getX} at every caller: those are deprecated for removal in
         * the version this compiles against, and a call that the compiler says is going away is a call that
         * goes away on somebody else's schedule.
         */
        private boolean cutShort(boolean allowed) {
            if (allowed) {
                return false;
            }
            if (told.compareAndSet(false, true)) {
                rule.refused(edit);
            }
            return true;
        }
    }
}
