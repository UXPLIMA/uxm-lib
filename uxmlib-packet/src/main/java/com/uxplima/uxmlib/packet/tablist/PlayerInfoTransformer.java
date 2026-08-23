package com.uxplima.uxmlib.packet.tablist;

import java.util.Set;

/**
 * Rewrites one entry of an outbound player-info packet.
 *
 * <p>{@code actions} is the immutable action set carried by the original packet. Returning {@code state}
 * unchanged is the cheap no-op path. A returned state must retain the same profile id; the adapter preserves
 * opaque profile and chat-session data and automatically adds action flags for every field that changed.
 */
@FunctionalInterface
public interface PlayerInfoTransformer {

    PlayerInfoState transform(PlayerInfoState state, Set<PlayerInfoAction> actions);
}
