package com.uxplima.uxmlib.content;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * What another plugin says a player's level in one of its skills is.
 *
 * <p>Read and never written. A skill plugin owns its own progression and we do not touch it: what a plugin
 * of ours wants is to be able to say "this row pays more to a player whose mining is above fifty", and that
 * is a read.
 *
 * <p>The skill is named by the operator, in the vendor's own spelling, because which skills a server has is
 * a question about that server. Nothing here carries a list of skill names.
 */
public interface SkillLevels {

    /** A seam that answers for nothing, which is a server running no skill plugin. */
    SkillLevels NONE = new SkillLevels() {

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public OptionalInt levelOf(UUID player, String skill) {
            return OptionalInt.empty();
        }
    };

    /** Whether any skill plugin answered on this server. */
    boolean active();

    /**
     * What this player's level in {@code skill} is, or nothing.
     *
     * <p>Nothing means three different things and the caller treats them all the same way: no skill plugin,
     * no such skill, or a player this plugin has never seen. Each of them is "there is no number here", and
     * a condition that reads one is not met.
     */
    OptionalInt levelOf(UUID player, String skill);
}
