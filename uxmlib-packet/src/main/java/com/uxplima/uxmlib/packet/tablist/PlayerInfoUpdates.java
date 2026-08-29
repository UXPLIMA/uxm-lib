package com.uxplima.uxmlib.packet.tablist;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmlib.packet.tablist.internal.NmsPlayerInfoUpdates;
import org.jspecify.annotations.Nullable;

/**
 * Rewrites an outgoing {@code ClientboundPlayerInfoUpdatePacket} so that the tab-list entries of selected
 * players are forced <em>unlisted</em>: present as known client entities (their skins and above-head names on
 * the real entity still work) but not shown as a row in the tab list. This is the clean mechanism for a
 * synthetic tab list that hides real players: rather than removing entries or dropping the packet (which would
 * also strip the entity), an interceptor passes each outbound player-info packet through {@link #forceUnlisted}
 * and forwards the rewritten copy, leaving any entry it owns (its own filler rows) untouched.
 *
 * <p>The 1.19.3+ tab protocol carries a per-entry {@code listed} flag; an entry with {@code listed=false} is not
 * drawn in the tab list. {@link #forceUnlisted} returns a new packet whose entries matching the
 * {@code suppress} predicate have {@code listed=false} and every other field of every entry (uuid, profile,
 * latency, game mode, display name, list order, the action set itself) left exactly as it was, so an
 * unmatched entry (a filler the caller added with {@code listed=true}) is carried through verbatim.
 *
 * <p>This class holds <em>no</em> {@code net.minecraft} reference: the actual entry reconstruction is in the
 * Mojang-mapped {@link NmsPlayerInfoUpdates}, which {@link #forceUnlisted} delegates to. The only logic here is
 * the pure, unit-testable {@link #affectsListing} guard. A rewrite is only meaningful when the packet's action
 * set actually carries the {@code listed} flag: an {@code ADD_PLAYER} (which seats the initial {@code listed}
 * state) or an {@code UPDATE_LISTED}. A packet whose actions touch neither (a pure latency or display-name
 * update) cannot change a client's tab visibility, so it is a no-op: {@link #forceUnlisted} returns {@code null}
 * and the caller forwards the original unchanged.
 */
public final class PlayerInfoUpdates {

    /** The action that seats an entry's initial {@code listed} state when it is added to the tab. */
    public static final String ADD_PLAYER = "ADD_PLAYER";

    /** The action that updates an existing entry's {@code listed} flag. */
    public static final String UPDATE_LISTED = "UPDATE_LISTED";

    private PlayerInfoUpdates() {}

    /**
     * Whether an action set carrying these action names can change a client's tab-list visibility: true when it
     * contains {@link #ADD_PLAYER} or {@link #UPDATE_LISTED}. A packet whose actions affect neither cannot be made
     * to hide a player by flipping {@code listed}, so forcing it unlisted is pointless (and the caller forwards the
     * original). Pure and NMS-free so the guard is unit-testable; the NMS path passes the live packet's action
     * names here before reconstructing.
     */
    public static boolean affectsListing(Iterable<String> actionNames) {
        Objects.requireNonNull(actionNames, "actionNames");
        for (String name : actionNames) {
            if (ADD_PLAYER.equals(name) || UPDATE_LISTED.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rewrite {@code packet} (an outbound {@code ClientboundPlayerInfoUpdatePacket}) so every entry whose uuid the
     * {@code suppress} predicate accepts is forced {@code listed=false}, returning the new packet, or {@code null}
     * when no rewrite is needed, in which case the caller forwards the original. {@code null} is returned when:
     * {@code packet} is not a player-info-update packet, the packet's action set {@link #affectsListing does not
     * affect listing}, or no entry matched the predicate (nothing to change). The predicate runs on a Netty I/O
     * thread, so it must be cheap, must not block, and must not touch the Bukkit API; a thread-safe snapshot of the
     * "real players to suppress" set is the intended shape.
     *
     * @param packet the raw outbound packet object; a non-player-info packet yields {@code null}
     * @param suppress accepts the uuid of a real-player entry that should be hidden (forced unlisted)
     * @return a rewritten packet, or {@code null} when the original should be forwarded unchanged
     */
    public static @Nullable Object forceUnlisted(Object packet, Predicate<UUID> suppress) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(suppress, "suppress");
        return NmsPlayerInfoUpdates.forceUnlisted(packet, suppress);
    }

    /**
     * Rewrite selected fields of every entry in an outbound player-info packet without losing its profile or
     * secure-chat session. Returns {@code null} when {@code packet} is not a player-info update or the transformer
     * changed nothing, allowing an interceptor to forward the original object without allocating a duplicate.
     *
     * <p>The transform may change listed state, latency, game mode, display name, hat visibility and list order.
     * The profile id is selection-only and must remain unchanged. Whenever a field changes, the implementation
     * adds that field's update action to the rebuilt packet so the client cannot silently ignore the new value.
     */
    public static @Nullable Object rewrite(Object packet, PlayerInfoTransformer transformer) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(transformer, "transformer");
        return NmsPlayerInfoUpdates.rewrite(packet, transformer);
    }
}
