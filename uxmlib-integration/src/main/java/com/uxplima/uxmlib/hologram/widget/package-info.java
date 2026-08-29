/**
 * Per-player hologram widgets built on the native Display layer and the lifecycle SPI:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.hologram.widget.PagedHologram}: N overlapping page holograms at one
 *       location, each viewer on its own page, {@code next}/{@code prev} re-rendering only that viewer.</li>
 *   <li>{@link com.uxplima.uxmlib.hologram.widget.SwitchableHologram}: per-player conditional content; the
 *       first {@link com.uxplima.uxmlib.hologram.widget.SwitchSelection} state whose predicate passes wins,
 *       mirroring the GUI {@code Stateful} pattern.</li>
 *   <li>{@link com.uxplima.uxmlib.hologram.widget.LeaderboardHologram}: a live leaderboard tying the pure
 *       {@code LeaderboardRenderer} to a hologram refreshed on a {@code Scheduler} timer.</li>
 * </ul>
 *
 * <p>Each widget keeps its page/state math pure (see {@link com.uxplima.uxmlib.hologram.widget.PageState} and
 * {@link com.uxplima.uxmlib.hologram.widget.SwitchSelection}) and pushes show/hide through a presenter seam so
 * it is unit-testable with no live entity. The paged and switchable widgets implement
 * {@link com.uxplima.uxmlib.hologram.HologramLifecycle}, so registered with the {@code HologramManager} they
 * reset per-player state on quit / world-change without a per-consumer Bukkit listener.
 */
@NullMarked
package com.uxplima.uxmlib.hologram.widget;

import org.jspecify.annotations.NullMarked;
