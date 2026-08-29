package com.uxplima.uxmlib.packet.npc;

/**
 * The body poses a fake NPC can be frozen in: the player-visible subset of the server's full {@code
 * net.minecraft.world.entity.Pose} list. A pose is carried on the entity's {@code DATA_POSE} metadata field, so
 * this enum names each one port-side to keep the {@link NpcPackets#pose} contract free of {@code net.minecraft}.
 * The constant names match {@code Pose}'s own, so the NMS implementation maps one to the other by name with no
 * lookup table to drift: except {@link #GLIDING}, the friendlier name for the server's {@code FALL_FLYING}
 * elytra pose, which carries its server name on the constant.
 */
public enum NpcPose {

    /** The default upright pose. */
    STANDING("STANDING"),
    /** Lying down, as in a bed. */
    SLEEPING("SLEEPING"),
    /** The horizontal swimming pose. */
    SWIMMING("SWIMMING"),
    /** The elytra-glide pose; the server names this {@code FALL_FLYING}. */
    GLIDING("FALL_FLYING"),
    /** The sneaking crouch. */
    CROUCHING("CROUCHING"),
    /** The riptide spin-attack pose. */
    SPIN_ATTACK("SPIN_ATTACK"),
    /** The seated pose. */
    SITTING("SITTING");

    private final String serverName;

    NpcPose(String serverName) {
        this.serverName = serverName;
    }

    /**
     * The matching {@code net.minecraft.world.entity.Pose} constant name. Equal to {@link #name()} for every pose
     * but {@link #GLIDING}, whose server counterpart is {@code FALL_FLYING}. The NMS implementation resolves the
     * real {@code Pose} from this name, so the mapping lives on the constant rather than in a switch that drifts.
     */
    public String serverName() {
        return serverName;
    }
}
