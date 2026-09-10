package com.uxplima.uxmlib.content;

import java.util.Optional;

import org.bukkit.entity.Entity;

/**
 * What a custom mob plugin calls one mob, and how many that one mob counts for.
 *
 * <p>Two questions and one seam, because they are asked in the same breath. A jobs plugin paying for a kill
 * needs to know that this zombie is a boss the operator priced separately, and it needs to know that the
 * stack it just killed was forty of them rather than one. Answering the first and not the second pays a
 * fortieth of what the player earned.
 */
public interface CustomMobs {

    /** A seam that answers for nothing, which is a server running no custom mob plugin. */
    CustomMobs NONE = new CustomMobs() {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public Optional<String> idOf(Entity entity) {
            return Optional.empty();
        }

        @Override
        public int countOf(Entity entity) {
            return 1;
        }
    };

    /** Whether any custom mob plugin answered on this server. */
    boolean active();

    /**
     * What this mob is called, or nothing when it is an ordinary one.
     *
     * <p>Namespaced by the vendor, as {@code mythicmobs:skeleton_king}, so two vendors that both ship a
     * skeleton king are two different mobs and a file can say which.
     */
    Optional<String> idOf(Entity entity);

    /**
     * How many mobs this one entity stands for.
     *
     * <p>One on a server with no stacker, and forty on a server whose stacker just handed a player a stack
     * of forty. A caller that ignores this pays a fortieth of what the player earned, which is the whole
     * reason the question is on this seam rather than left to each plugin.
     */
    int countOf(Entity entity);
}
