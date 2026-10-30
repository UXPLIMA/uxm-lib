/**
 * Leaderboards in the grammar every UXM plugin shares: a board ranks players by one number, in every period that
 * runs (all time, this month, this week, today), and answers {@code top_<board>_<period>_<n>_name|value|head},
 * {@code rank_<board>_<period>}, {@code value_<board>_<period>} and {@code resets_<period>}.
 *
 * <p>A plugin names its boards ({@link com.uxplima.uxmlib.board.Board}), hands each event to a {@link
 * com.uxplima.uxmlib.board.BoardRecorder}, refreshes a {@link com.uxplima.uxmlib.board.Leaderboard} on a timer and
 * answers its placeholders through {@link com.uxplima.uxmlib.board.BoardAnswers}. A counter or a record is kept by
 * a {@link com.uxplima.uxmlib.board.BoardStore}; {@link com.uxplima.uxmlib.board.SqlBoardStore} keeps it in the
 * plugin's own database. A board that ranks what a player holds now, such as a balance, reads the plugin's own
 * data through a {@link com.uxplima.uxmlib.board.Snapshot} and runs for all time only.
 *
 * <p>The module names no wording and no colour: the words for nobody, none and the time left, and how a score is
 * shown, come from the plugin through {@link com.uxplima.uxmlib.board.BoardText}.
 */
@NullMarked
package com.uxplima.uxmlib.board;

import org.jspecify.annotations.NullMarked;
