package com.uxplima.uxmlib.packet.npc;

/**
 * The wire encoding for a rotation angle: a degree is packed into a single signed byte covering the full circle
 * as 256 steps. This mirrors the server's own {@code Mth.packDegrees} ({@code floor(deg * 256 / 360)}), so a
 * value built here lands on the same byte the server would write: keeping the look and teleport packets, which
 * take a pre-packed byte, in step with the spawn packet, which packs raw degrees itself. Kept pure and
 * NMS-free so the conversion is unit-tested directly.
 */
public final class ByteAngle {

    private ByteAngle() {}

    /** Pack {@code degrees} into a signed byte, matching {@code Mth.packDegrees}. */
    public static byte of(float degrees) {
        return (byte) Math.floor(degrees * 256.0f / 360.0f);
    }
}
