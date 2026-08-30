package com.uxplima.uxmlib.text;

import java.util.Objects;

/**
 * Pure derivation of which render flourishes are safe for a given
 * {@link ClientProfile}. A {@code false} flag means the recipient cannot render
 * the corresponding MiniMessage feature.
 *
 * <p>Nothing applies this on its own. A consumer computes the capabilities once
 * per recipient and passes them to {@link RenderDowngrade#apply}, which returns
 * the MiniMessage source to parse for that recipient. The message sink does not
 * call it: a downgrade needs a per-viewer profile that only the consumer's own
 * Floodgate and ViaVersion hooks can supply.
 *
 * <p>The downgrade a consumer applies is invisible: the message always
 * delivers; only the unsupported flourish is degraded (gradient flattened to
 * its first colour, custom-font tag removed, {@code show_item} hover falling
 * back to {@code show_text}).
 *
 * <p>Capability flags:
 * <ul>
 *   <li>{@link #gradient()}: false on Bedrock (Geyser cannot render gradients).</li>
 *   <li>{@link #customFont()}: false on Bedrock (no custom-font support).</li>
 *   <li>{@link #hoverItem()}: protocol-gated by {@link #MIN_PROTOCOL_FOR_HOVER_ITEM}.</li>
 * </ul>
 */
public record ClientCapabilities(boolean gradient, boolean customFont, boolean hoverItem) {

    /**
     * Minimum client protocol number that can render a {@code show_item} hover.
     * {@code 0} would mean "always allow" but show_item has been stable across
     * every protocol the plugin targets, so the conservative threshold is also
     * the floor: the only client that fails it is one explicitly reporting a
     * sub-{@code 0} version, which never happens for a real client. Operators
     * raise this via {@code protocol.min-protocol-for-hover-item} when a
     * specific legacy ViaVersion target misbehaves; the
     * {@link ClientProfile#CURRENT_PROTOCOL} sentinel always clears it.
     */
    public static final int MIN_PROTOCOL_FOR_HOVER_ITEM = 0;

    /**
     * Derive the safe capability set for {@code profile} using the default
     * {@link #MIN_PROTOCOL_FOR_HOVER_ITEM} hover-item threshold. Bedrock loses
     * gradient and custom-font; hover-item is gated on the protocol threshold.
     */
    public static ClientCapabilities of(ClientProfile profile) {
        return of(profile, MIN_PROTOCOL_FOR_HOVER_ITEM);
    }

    /**
     * Derive the safe capability set for {@code profile} using an
     * operator-configured {@code minProtocolForHoverItem} threshold (P50
     * {@code protocol.min-protocol-for-hover-item}). The
     * {@link ClientProfile#CURRENT_PROTOCOL} sentinel always clears the
     * threshold so an absent ViaVersion never downgrades hover-item.
     */
    public static ClientCapabilities of(ClientProfile profile, int minProtocolForHoverItem) {
        Objects.requireNonNull(profile, "profile");
        boolean hoverItem = profile.protocolVersion() >= minProtocolForHoverItem;
        return new ClientCapabilities(profile.supportsGradient(), profile.supportsCustomFont(), hoverItem);
    }
}
