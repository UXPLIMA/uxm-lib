package com.uxplima.uxmlib.packet.display;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * The seam between pure per-viewer text logic and the NMS packet construction for an <em>existing</em>
 * {@code TextDisplay}. Every packet crosses this boundary as an opaque {@link Object}, so this interface, and
 * everything that depends on it, carries no {@code net.minecraft} reference and stays unit-testable against a
 * fake. The single implementation that builds a real {@code ClientboundSetEntityDataPacket} against the
 * Mojang-mapped dev bundle lives behind this port in {@code display.internal}.
 *
 * <p>This is the per-viewer text override: a hologram is one real, server-side, shared {@code TextDisplay}
 * entity whose broadcast text is what every viewer sees by default. To give one viewer their own text: their
 * own resolved placeholder values, say, without spawning a private entity per viewer, send <em>that</em>
 * viewer a metadata packet that sets only the entity's text component to their text. The client applies it over
 * the shared entity it already tracks, so the viewer sees their text while everyone else keeps the broadcast
 * text. {@link #textOverride(int, Component)} builds one such packet for an entity id; {@link #send(Player,
 * Object)} writes it to a viewer's connection, so the same packet can be re-sent to one viewer cheaply.
 */
public interface DisplayTextPackets {

    /**
     * Build the set-entity-data packet that overrides only the text component of the {@code TextDisplay} with
     * id {@code entityId} for whichever viewer it is sent to. The packet carries just the text value, so the
     * entity's other metadata (billboard, background, scale, …) is untouched: the client keeps what the shared
     * spawn gave it.
     */
    Object textOverride(int entityId, Component text);

    /** Write {@code packet} to {@code viewer}'s connection. A no-op if the connection cannot be resolved. */
    void send(Player viewer, Object packet);
}
