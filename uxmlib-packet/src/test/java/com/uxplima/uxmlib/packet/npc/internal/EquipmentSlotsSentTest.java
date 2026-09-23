package com.uxplima.uxmlib.packet.npc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.packet.npc.EquipmentSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * An equipment packet always names at least one slot, because the protocol cannot say "none".
 *
 * <p>The client reads a slot, then reads on while a continuation bit is set, so a packet with no slot in it is one
 * byte short and does not decode. uxmEssentials strips an invisible posed player's gear by sending an empty map,
 * which meant exactly that packet every half second: on 2026-09-23 a Folia server behind ViaVersion logged "ERROR IN
 * Protocol26_2To26_1 IN REMAP OF SET_EQUIPMENT" three hundred times after one {@code /bellyflop}, and a client with
 * no translator in front of it reads the same short packet. An empty map means every slot is bare, which is what
 * the only caller meant, and it goes out as every slot, empty.
 */
class EquipmentSlotsSentTest {

    @Test
    @DisplayName("an empty map sends every slot, so the packet names what it clears")
    void anEmptyMapSendsEverySlot() {
        assertThat(EquipmentSlots.sent(Map.of())).containsExactly(EquipmentSlot.values());
    }

    @Test
    @DisplayName("a map with items sends those slots and no other")
    void aFilledMapSendsItsOwnSlots() {
        ItemStack helmet = Mockito.mock(ItemStack.class);

        assertThat(EquipmentSlots.sent(Map.of(EquipmentSlot.HEAD, helmet))).containsExactly(EquipmentSlot.HEAD);
    }
}
