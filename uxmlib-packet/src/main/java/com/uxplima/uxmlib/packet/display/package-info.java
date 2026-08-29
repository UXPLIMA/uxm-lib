/**
 * The per-viewer text-override layer for an existing {@code TextDisplay}: a pure port
 * ({@link com.uxplima.uxmlib.packet.display.DisplayTextPackets}) that builds a metadata packet setting only the
 * text component of a shared display entity for one viewer. It is the FancyHolograms approach to per-viewer
 * holograms (one real shared entity, plus a per-viewer text override packet) done without a packet-entity
 * rewrite. Everything here is NMS-free; the single Mojang-mapped implementation sits in {@code display.internal}.
 */
@NullMarked
package com.uxplima.uxmlib.packet.display;

import org.jspecify.annotations.NullMarked;
