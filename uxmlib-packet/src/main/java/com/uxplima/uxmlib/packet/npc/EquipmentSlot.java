package com.uxplima.uxmlib.packet.npc;

/**
 * The six equipment slots a fake-player NPC can wear, in the order the protocol enumerates them. This is a small
 * port-side enum so the {@link NpcPackets} contract (and everything that depends on it) names a slot without
 * reaching for {@code org.bukkit.inventory.EquipmentSlot} or {@code net.minecraft}'s own slot type. The NMS
 * implementation maps each constant onto the matching server slot; the mapping is the single place those two
 * names meet.
 */
public enum EquipmentSlot {

    /** The item held in the main hand. */
    MAINHAND,
    /** The item held in the off hand. */
    OFFHAND,
    /** The helmet slot. */
    HEAD,
    /** The chestplate slot. */
    CHEST,
    /** The leggings slot. */
    LEGS,
    /** The boots slot. */
    FEET;
}
