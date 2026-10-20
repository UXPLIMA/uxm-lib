package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Where a plugin puts an item it hands a player and keeps. Each case is one way a shared hotbar goes wrong: another
 * item moved aside and handed out again, a player's own item written over, a second copy after the player moved the
 * first, and an inventory with no room at all.
 */
class HeldItemsTest {

    private static final NamespacedKey MARK = new NamespacedKey("test", "tool");

    private PlayerInventory inventory;

    @BeforeEach
    void start() {
        inventory = MockBukkit.mock().addPlayer().getInventory();
        inventory.clear();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an empty wanted slot takes the item")
    void anEmptySlotTakesTheItem() {
        assertThat(HeldItems.place(inventory, 4, tool(), HeldItemsTest::isOurs)).isEqualTo(4);
        assertThat(count(Material.COMPASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("an item already in the wanted slot is never written over or moved")
    void anotherItemIsNeitherWrittenOverNorMoved() {
        inventory.setItem(4, new ItemStack(Material.ENDER_CHEST));

        assertThat(HeldItems.place(inventory, 4, tool(), HeldItemsTest::isOurs)).isEqualTo(0);
        assertThat(inventory.getItem(4)).extracting(ItemStack::getType).isEqualTo(Material.ENDER_CHEST);
    }

    @Test
    @DisplayName("placing again, as every join does, never adds a copy of either item")
    void placingAgainAddsNoCopy() {
        for (int join = 0; join < 3; join++) {
            inventory.setItem(4, new ItemStack(Material.ENDER_CHEST));
            HeldItems.place(inventory, 4, tool(), HeldItemsTest::isOurs);
        }

        assertThat(count(Material.ENDER_CHEST)).isEqualTo(1);
        assertThat(count(Material.COMPASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("an item the player moved stays where they put it while its own slot is taken")
    void aMovedItemKeepsItsPlace() {
        inventory.setItem(4, new ItemStack(Material.ENDER_CHEST));
        inventory.setItem(6, tool());

        assertThat(HeldItems.place(inventory, 4, tool(), HeldItemsTest::isOurs)).isEqualTo(6);
        assertThat(count(Material.COMPASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("an item that waited elsewhere goes back to its own slot once that slot is free")
    void itReturnsToItsSlot() {
        inventory.setItem(6, tool());

        assertThat(HeldItems.place(inventory, 4, tool(), HeldItemsTest::isOurs)).isEqualTo(4);
        assertThat(inventory.getItem(6)).isNull();
        assertThat(count(Material.COMPASS)).isEqualTo(1);
    }

    @Test
    @DisplayName("a full inventory gets nothing and loses nothing")
    void aFullInventoryGetsNothing() {
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE));
        }

        assertThat(HeldItems.place(inventory, 4, tool(), HeldItemsTest::isOurs)).isEqualTo(-1);
        assertThat(count(Material.STONE)).isEqualTo(inventory.getStorageContents().length);
    }

    @Test
    @DisplayName("taking back removes every copy and nothing else")
    void takingBackRemovesEveryCopy() {
        inventory.setItem(1, tool());
        inventory.setItem(7, tool());
        inventory.setItem(3, new ItemStack(Material.DIAMOND));

        assertThat(HeldItems.takeBack(inventory, HeldItemsTest::isOurs)).isEqualTo(2);
        assertThat(count(Material.COMPASS)).isZero();
        assertThat(count(Material.DIAMOND)).isEqualTo(1);
    }

    private static ItemStack tool() {
        ItemStack item = new ItemStack(Material.COMPASS);
        Items.editPdc(item, pdc -> pdc.set(MARK, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    private int count(Material material) {
        return Arrays.stream(inventory.getContents())
                .filter(Objects::nonNull)
                .filter(item -> item.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private static boolean isOurs(ItemStack item) {
        return Pdc.read(item).has(MARK, PersistentDataType.BYTE);
    }
}
