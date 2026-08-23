/**
 * The packet tab-list layer: a pure port ({@link com.uxplima.uxmlib.packet.tablist.TabListPackets}) plus the
 * value types it speaks in ({@link com.uxplima.uxmlib.packet.tablist.TabEntry},
 * {@link com.uxplima.uxmlib.packet.tablist.TabSkin}). It builds per-viewer tab-list rows — custom display
 * name, client-side sort order, custom skin — the things native Paper cannot do per viewer. The
 * {@link com.uxplima.uxmlib.packet.tablist.PlayerInfoPackets} port is the complete, batch-oriented API for
 * synthetic player-info entries; {@code TabListPackets} remains its backwards-compatible convenience facade. The
 * {@link com.uxplima.uxmlib.packet.tablist.PlayerInfoUpdates} helper rewrites an outbound player-info packet to
 * force selected real players {@code listed=false} (the mechanism for a synthetic tab list that hides real
 * players). Everything here is NMS-free; the single Mojang-mapped implementation sits in {@code tablist.internal}.
 */
@NullMarked
package com.uxplima.uxmlib.packet.tablist;

import org.jspecify.annotations.NullMarked;
