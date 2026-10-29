package com.uxplima.uxmlib.condition.currency;

import java.util.Optional;
import java.util.UUID;

/**
 * Who the server has seen.
 *
 * <p>An operator types a name and the plugin keeps ids, so one of the two has to be turned into the other.
 * It is a port because a name is a server question, and because a test must be able to answer it without
 * one.
 */
public interface KnownPlayers {

    /** The id of the player with this name, absent when the server has never seen the name. */
    Optional<UUID> byName(String name);

    /**
     * The name of the player with this id, absent when the server has never seen them.
     *
     * <p>A command that an operator wrote takes a name, so the plugin has to turn its id back into one.
     */
    Optional<String> nameOf(UUID id);
}
