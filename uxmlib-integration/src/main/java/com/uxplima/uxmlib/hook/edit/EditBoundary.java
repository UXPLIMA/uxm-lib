package com.uxplima.uxmlib.hook.edit;

import java.util.UUID;

/**
 * Whether one block of somebody's edit may change, and what to do when one may not.
 *
 * <p>The consumer's own rule. This library has no opinion about where anybody may build: it knows that an
 * edit touches blocks one at a time and that somebody has to be asked.
 *
 * <p>It is asked per block, so it has to be cheap. A consumer whose answer needs a lookup should work out
 * what it can from the coordinates and remember the rest for the run of one edit: a fill of a hundred
 * thousand blocks asks a hundred thousand times.
 *
 * <p>It is asked on whichever thread the editor is running on, which is usually not the main one, so it
 * must not touch the server. Everything it could want about who is editing is on {@link Edit}, which the
 * editor answers itself and is safe to read from there.
 */
public interface EditBoundary {

    /**
     * Who is editing, and what can be asked about them without touching the server.
     *
     * <p>{@link #holds} is here for exactly one reason: a consumer's rule almost always has a node that
     * excuses somebody, and asking the server for it from the editor's thread is the thing that must not
     * happen. The editor answers its own permission questions from wherever it already holds them.
     */
    interface Edit {

        /** Who is editing. */
        UUID player();

        /** The world the edit is in, by the name the server gives it. */
        String world();

        /** Whether the player holds a permission node. */
        boolean holds(String permission);
    }

    /** Whether this edit may change the block at these coordinates. */
    boolean mayChange(Edit edit, int x, int y, int z);

    /**
     * Told once, the first time an edit is cut short.
     *
     * <p>Once per edit and not once per block, because an edit that crosses a boundary crosses it for
     * every block on the far side and a player who is told a hundred thousand times has been kicked by
     * their own client. What to say is the consumer's, and saying nothing is a legitimate answer.
     */
    default void refused(Edit edit) {}
}
