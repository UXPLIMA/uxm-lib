package com.uxplima.uxmlib.hook.edit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import com.uxplima.uxmlib.hook.Hooks;
import org.jspecify.annotations.Nullable;

/**
 * A boundary over FastAsyncWorldEdit.
 *
 * <p>FAWE answers WorldEdit's API and announces every edit the same way, but it does not honour the extent a
 * listener hands back. It throws that extent away unless the server's own FAWE config names its class under
 * {@code extent.allowed-plugins}, and its fast paths write a chunk at a time past any extent it does keep. So
 * {@link WorldEditGuard} on a FAWE server guards nothing, and says nothing about it. What FAWE honours is a
 * processor: every chunk an edit writes passes through the processors of that edit before it reaches the world.
 * The rule rides there, in {@link BoundedChunks}.
 *
 * <p>An edit FAWE writes straight into the world, with no queue to carry a processor, is cancelled. That happens
 * only when an operator turns FAWE's fast placement off, and an edit this cannot ask about is not an edit this
 * lets through.
 *
 * <p>Built by {@link WorldEditGuard#find()} by name, from the half of this module that compiles against FAWE, so
 * a server without FAWE never links a class of it.
 */
public final class FaweEditGuard implements EditGuard {

    static final String PLUGIN = "FastAsyncWorldEdit";

    /** The rule in force, or nothing when none has been installed. Read on FAWE's own threads. */
    private final AtomicReference<@Nullable EditBoundary> boundary = new AtomicReference<>();

    /** Whether the handler is on the bus, so a second install does not put it on twice. */
    private final AtomicBoolean listening = new AtomicBoolean();

    /** Public and without arguments, because it is built by name from the other half of this module. */
    public FaweEditGuard() {}

    @Override
    public String pluginName() {
        return PLUGIN;
    }

    @Override
    public boolean isAvailable() {
        return Hooks.isPresent(PLUGIN);
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

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        EditBoundary rule = boundary.get();
        if (rule != null) {
            guard(event, rule);
        }
    }

    /**
     * Put the rule into the queue this edit writes through.
     *
     * <p>At {@code BEFORE_CHANGE}, the stage every write passes, as {@link WorldEditGuard} does. An edit with no
     * player behind it is the console's or another plugin's and is left alone, for the same reason it is there.
     */
    static void guard(EditSessionEvent event, EditBoundary rule) {
        Actor actor = event.getActor();
        World world = event.getWorld();
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE
                || actor == null
                || world == null
                || !actor.isPlayer()) {
            return;
        }
        EditBoundary.Edit edit = new WorldEditGuard.Editing(actor, world.getName());
        Extent extent = event.getExtent();
        Extent carried = extent.addProcessor(new BoundedChunks(edit, rule, FaweEditGuard::blockId));
        if (carried != extent) {
            // No queue took the processor, so the edit would be written without it.
            event.setCancelled(true);
            rule.refused(edit);
        }
    }

    private static String blockId(int cell) {
        return BlockTypesCache.states[cell].getBlockType().id();
    }
}
