package com.uxplima.uxmlib.board;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * A board read from what the plugin holds now rather than counted from events: a balance, a level, a prestige.
 *
 * <p>Such a board has no history, so it runs for all time only. {@link #top} and {@link #countByScore} are called on
 * the async lane by {@link Leaderboard#refresh()} and may block; {@link #scoreOf} is called while a placeholder is
 * drawn and answers from memory.
 */
public interface Snapshot {

    /** The highest scores first; of equal scores, the order the plugin keeps. Only scores above zero. */
    List<Ranked> top(int limit);

    /** How many players hold each score above zero. A place is one more than those above it. */
    Map<Long, Integer> countByScore();

    /** What {@code player} holds now, or empty when the plugin does not know without a blocking read. */
    OptionalLong scoreOf(UUID player);
}
