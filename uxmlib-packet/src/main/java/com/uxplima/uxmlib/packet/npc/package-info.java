/**
 * The packet NPC layer: a pure port ({@link com.uxplima.uxmlib.packet.npc.NpcPackets}) for the primitives that
 * spawn and steer a fake-player NPC: the player-info entry that carries its skin, the entity spawn, the head
 * and body look packets, teleport, and removal. Everything here is NMS-free; the byte-angle encoding
 * ({@link com.uxplima.uxmlib.packet.npc.ByteAngle}) is pure and unit-tested, and the single Mojang-mapped
 * implementation sits in {@code npc.internal}. The skin value type is reused from the tablist layer.
 */
@NullMarked
package com.uxplima.uxmlib.packet.npc;

import org.jspecify.annotations.NullMarked;
