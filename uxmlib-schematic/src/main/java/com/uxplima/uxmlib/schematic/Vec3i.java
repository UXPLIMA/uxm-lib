package com.uxplima.uxmlib.schematic;

/** A position or a distance in whole blocks. */
public record Vec3i(int x, int y, int z) {

    public static final Vec3i ZERO = new Vec3i(0, 0, 0);

    public Vec3i plus(Vec3i other) {
        return new Vec3i(x + other.x, y + other.y, z + other.z);
    }

    public Vec3i minus(Vec3i other) {
        return new Vec3i(x - other.x, y - other.y, z - other.z);
    }
}
