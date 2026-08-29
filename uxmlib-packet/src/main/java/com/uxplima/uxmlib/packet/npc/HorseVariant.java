package com.uxplima.uxmlib.packet.npc;

/**
 * The wire encoding for a horse's appearance: the coat colour and the body markings pack into the single integer
 * the horse's {@code DATA_ID_TYPE_VARIANT} metadata field carries. This mirrors the server's own
 * {@code Horse.setVariantAndMarkings} ({@code color & 0xFF | markings << 8 & 0xFF00}): the colour in the low
 * byte, the markings in the high byte, so a value built here lands on the same integer the server would write.
 * Kept pure and NMS-free so the packing is unit-tested directly, the same precedent as {@link ByteAngle}.
 */
public final class HorseVariant {

    private HorseVariant() {}

    /**
     * Pack {@code color} (the coat colour, 0 to 6) and {@code markings} (0 to 4) into the variant integer. Each
     * component is masked to its byte so an out-of-range value cannot bleed into the other; the caller clamps to
     * the real ranges, this is the defensive floor.
     */
    public static int pack(int color, int markings) {
        return (color & 0xFF) | ((markings << 8) & 0xFF00);
    }
}
