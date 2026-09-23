package com.uxplima.uxmlib.hook.edit;

import java.util.Objects;

import com.uxplima.uxmlib.hook.Hooks;

/**
 * Holding a boundary over whatever world editor the server has, or over none at all.
 *
 * <p>A consumer installs one rule and never asks which editor is installed, or whether one is. A server
 * with no editor gets {@link #none()}, which answers that it is not available and does nothing, so the
 * consumer's own code has no branch in it.
 *
 * <p>Installing twice replaces the rule. Uninstalling twice is not an error: a plugin disabling is a plugin
 * that may already have been disabled.
 */
public interface EditGuard {

    /** The editor this guards, for a line in a console or a doctor. */
    String pluginName();

    /** Whether there is an editor here to guard at all. */
    boolean isAvailable();

    /** Put {@code boundary} in front of every edit from now on. */
    void install(EditBoundary boundary);

    /** Take it away again. */
    void uninstall();

    /**
     * A guard for a server with no world editor.
     *
     * <p>Not null and not an empty optional, because a consumer that had to check would grow the one
     * branch this whole seam exists to remove.
     */
    static EditGuard none() {
        return Nothing.INSTANCE;
    }

    /**
     * The guard for whatever is installed, which is {@link #none()} when nothing is.
     *
     * <p>The presence check is made here, before anything names {@link WorldEditGuard}. Running that class's own
     * {@code find()} links the class, and linking it loads WorldEdit's extent types, so on a server without WorldEdit
     * the check inside it never ran: the call threw {@code NoClassDefFoundError} and the plugin asking failed to
     * enable. This interface names no WorldEdit type, so it links anywhere.
     */
    static EditGuard forServer() {
        if (!Hooks.isPresent(WorldEditNames.FAST_ASYNC) && !Hooks.isPresent(WorldEditNames.WORLD_EDIT)) {
            return none();
        }
        return WorldEditGuard.find().orElseGet(EditGuard::none);
    }

    /** {@link #none()}, as a class, because an anonymous one cannot carry the reason. */
    final class Nothing implements EditGuard {

        private static final EditGuard INSTANCE = new Nothing();

        private Nothing() {}

        @Override
        public String pluginName() {
            return "none";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void install(EditBoundary boundary) {
            Objects.requireNonNull(boundary, "boundary");
        }

        @Override
        public void uninstall() {}
    }
}
