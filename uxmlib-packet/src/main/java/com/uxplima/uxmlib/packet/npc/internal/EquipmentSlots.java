package com.uxplima.uxmlib.packet.npc.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.packet.npc.EquipmentSlot;

/**
 * Which slots an equipment packet names.
 *
 * <p>The protocol cannot say "none". The client reads one slot and reads on while a continuation bit is set, so a
 * packet with no slot is one byte short and does not decode, and an empty map sent exactly that packet, though
 * {@code NpcPackets.equipment} said an empty map strips every slot. It is sent as every slot, empty.
 */
final class EquipmentSlots {

    private EquipmentSlots() {}

    /** The slots given, or every slot when none is. */
    static List<EquipmentSlot> sent(Map<EquipmentSlot, ItemStack> items) {
        Objects.requireNonNull(items, "items");
        return items.isEmpty() ? List.of(EquipmentSlot.values()) : List.copyOf(items.keySet());
    }
}
