package com.uxplima.uxmlib.schematic.paper;

import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.StructureRotation;

/**
 * A turn about the vertical axis, seen from above, in quarter turns.
 *
 * <p>A block turns about the block it is pasted around, so a block a step east of it lands a step south of it
 * after a clockwise quarter turn. A position between blocks, such as an entity's, turns about the middle of
 * that block, so an entity standing in the middle of a block stays in the middle of the block it lands in.
 */
public enum Rotation {
    NONE(StructureRotation.NONE, 0),
    CLOCKWISE_90(StructureRotation.CLOCKWISE_90, 1),
    CLOCKWISE_180(StructureRotation.CLOCKWISE_180, 2),
    COUNTERCLOCKWISE_90(StructureRotation.COUNTERCLOCKWISE_90, 3);

    private final StructureRotation structure;
    private final int quarters;

    Rotation(StructureRotation structure, int quarters) {
        this.structure = structure;
        this.quarters = quarters;
    }

    /** The rotation the server turns block data by. */
    public StructureRotation structure() {
        return structure;
    }

    /** How many clockwise quarter turns this is. */
    public int quarters() {
        return quarters;
    }

    /** The turn that undoes this one. */
    public Rotation inverse() {
        return switch (this) {
            case NONE -> NONE;
            case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
            case CLOCKWISE_180 -> CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
        };
    }

    /** The rotation of {@code degrees} clockwise, which must be a whole number of quarter turns. */
    public static Rotation ofDegrees(int degrees) {
        if (degrees % 90 != 0) {
            throw new IllegalArgumentException("a rotation is a whole number of quarter turns, not " + degrees);
        }
        return switch (Math.floorMod(degrees / 90, 4)) {
            case 0 -> NONE;
            case 1 -> CLOCKWISE_90;
            case 2 -> CLOCKWISE_180;
            default -> COUNTERCLOCKWISE_90;
        };
    }

    /** Where {@code x} of a block {@code x, z} from the pivot lands. */
    public int x(int x, int z) {
        return switch (this) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    /** Where {@code z} of a block {@code x, z} from the pivot lands. */
    public int z(int x, int z) {
        return switch (this) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    /** Where {@code x} of a position {@code x, z} from the pivot's middle lands, from the pivot's middle. */
    public double x(double x, double z) {
        return switch (this) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    /** Where {@code z} of a position {@code x, z} from the pivot's middle lands, from the pivot's middle. */
    public double z(double x, double z) {
        return switch (this) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    /** A facing after the turn. Up and down, and anything that is not one of the eight, stay as they are. */
    public BlockFace face(BlockFace face) {
        BlockFace turned = face;
        for (int i = 0; i < quarters; i++) {
            turned = switch (turned) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                case NORTH_EAST -> BlockFace.SOUTH_EAST;
                case SOUTH_EAST -> BlockFace.SOUTH_WEST;
                case SOUTH_WEST -> BlockFace.NORTH_WEST;
                case NORTH_WEST -> BlockFace.NORTH_EAST;
                default -> turned;
            };
        }
        return turned;
    }

    /** A yaw after the turn, in the game's degrees. */
    public float yaw(float yaw) {
        return yaw + 90f * quarters;
    }
}
