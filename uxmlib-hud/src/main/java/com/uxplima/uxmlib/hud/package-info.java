/**
 * Adventure-native HUD overlays. Each surface delivers through Paper's own player APIs: no packets, no
 * NMS, so the module depends only on {@code uxmlib-common} (its {@code Scheduler} and {@code Text}).
 * {@link com.uxplima.uxmlib.hud.Titles} shows title/subtitle, {@link com.uxplima.uxmlib.hud.Tablist} sets
 * the player-list header/footer, {@link com.uxplima.uxmlib.hud.ActionBarManager} keeps a sticky action bar
 * alive past its vanilla fade with one shared re-send timer, and {@link com.uxplima.uxmlib.hud.BossBarManager}
 * drives boss bars through a mode enum (permanent, filling, countdown, dynamic) off one shared timer. The
 * flicker-free sidebar lives in the
 * {@code scoreboard} sub-package. Every manager is constructor-injected and holds its per-player state on
 * the instance; nothing here is a static singleton.
 */
@NullMarked
package com.uxplima.uxmlib.hud;

import org.jspecify.annotations.NullMarked;
