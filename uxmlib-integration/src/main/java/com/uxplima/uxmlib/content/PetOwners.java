package com.uxplima.uxmlib.content;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Entity;

/**
 * Whose pet killed that.
 *
 * <p>None of the competitors reads this and it is the reason it is here. A player whose pet does the killing
 * is a player doing the killing, and a plugin that pays nothing for it is a plugin that punishes a whole
 * playstyle by accident. The question is one line and the answer changes an evening's work into an evening's
 * wages.
 */
public interface PetOwners {

    /** A seam that answers for nothing, which is a server running no pet plugin. */
    PetOwners NONE = new PetOwners() {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<UUID> ownerOf(Entity entity) {
            return Optional.empty();
        }
    };

    /** Whether any pet plugin answered on this server. */
    boolean active();

    /** Whose pet this is, or nothing when it is not one. */
    Optional<UUID> ownerOf(Entity entity);
}
