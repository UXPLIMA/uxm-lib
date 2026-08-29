/**
 * Flicker-free sidebar scoreboards built natively on Paper's {@code Scoreboard}/{@code Objective}/
 * {@code Team} API. Each line is a {@code Team} whose entry is a fixed, unique, invisible colour-code key
 * and whose visible text is carried in the team prefix, so a render only re-sends the lines that actually
 * changed: there is no full clear, hence no flicker. {@link com.uxplima.uxmlib.hud.scoreboard.Sidebar} is
 * the per-player facade (title + up to fifteen lines); {@link com.uxplima.uxmlib.hud.scoreboard.SidebarManager}
 * owns the per-player instances and cleans them up on quit.
 */
@NullMarked
package com.uxplima.uxmlib.hud.scoreboard;

import org.jspecify.annotations.NullMarked;
