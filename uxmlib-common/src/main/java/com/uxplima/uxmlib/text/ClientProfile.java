package com.uxplima.uxmlib.text;

/**
 * Per-recipient client fingerprint used to decide which render flourishes are
 * safe to deliver (P50 #193/#194). A {@code ClientProfile} is resolved once per
 * recipient (Bedrock via Floodgate, protocol version via ViaVersion) and drives
 * the silent render-downgrade in the message sink.
 *
 * <p>Two facts are captured:
 * <ul>
 *   <li>{@code bedrock}: true when the player joined through Floodgate/Geyser.
 *       Bedrock cannot render MiniMessage gradients or custom-font tags, so they
 *       are flattened/dropped for these recipients.</li>
 *   <li>{@code protocolVersion}: the client's Minecraft protocol number, or
 *       {@link #CURRENT_PROTOCOL} when detection is absent (ViaVersion not
 *       installed), the safe "assume the server's own modern protocol" default.</li>
 * </ul>
 *
 * <p>The capability predicates here are convenience mirrors of the derivations
 * in {@link ClientCapabilities}; both share the same thresholds so a single
 * source of truth governs the downgrade decision.
 */
public record ClientProfile(boolean bedrock, int protocolVersion) {

    /**
     * Sentinel protocol value meaning "detection unavailable: assume the
     * server's own (modern) protocol". Treated as above every finite feature
     * threshold so an absent ViaVersion never accidentally downgrades a capable
     * Java client. Chosen as {@link Integer#MAX_VALUE} so it dominates any real
     * protocol number a client could report.
     */
    public static final int CURRENT_PROTOCOL = Integer.MAX_VALUE;

    /**
     * The safe default profile when no detection ports are wired or the player
     * is not found: a modern Java client with full capabilities.
     */
    public static ClientProfile javaModern() {
        return new ClientProfile(false, CURRENT_PROTOCOL);
    }

    /** True when the client can render MiniMessage {@code <gradient>}: false on Bedrock. */
    public boolean supportsGradient() {
        return !bedrock;
    }

    /** True when the client can render custom-font tags: false on Bedrock. */
    public boolean supportsCustomFont() {
        return !bedrock;
    }

    /**
     * True when the client's protocol is at or above the
     * {@link ClientCapabilities#MIN_PROTOCOL_FOR_HOVER_ITEM} threshold (the
     * {@link #CURRENT_PROTOCOL} sentinel always qualifies). Hover-item is
     * protocol-gated, not Bedrock-gated: Geyser translates {@code show_item}.
     */
    public boolean supportsHoverItem() {
        return protocolVersion >= ClientCapabilities.MIN_PROTOCOL_FOR_HOVER_ITEM;
    }
}
