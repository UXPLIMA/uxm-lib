/**
 * The online-data lifecycle. {@link com.uxplima.uxmlib.data.OnlineDataManager} loads a player's value on
 * join, keeps it in memory while they are online, saves it on quit, and flushes every online value on a
 * periodic timer: all off-thread through the library {@code Scheduler}. It writes through the generic
 * {@link com.uxplima.uxmlib.data.DataStore} seam, so the manager lives here in {@code uxmlib-integration}
 * without depending on {@code uxmlib-storage}: the consumer wires the seam to their own backend.
 */
@NullMarked
package com.uxplima.uxmlib.data;

import org.jspecify.annotations.NullMarked;
