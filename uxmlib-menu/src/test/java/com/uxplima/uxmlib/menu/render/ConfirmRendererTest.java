package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The engine's own two-button window. It has no spec and no file behind it: the host hands it a {@link ConfirmLook}
 * and the two words, so the things worth pinning are which slot is which decision, that the two blocks are never
 * swapped, and that each button carries the word it was handed.
 *
 * <p>The last one is the rule about inline literals in test form. The buttons used to carry an empty name, and the
 * client put the block's own English name in its place, which is the one thing a catalog-driven window must never
 * show.
 */
class ConfirmRendererTest {

    private Inventory inv;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        inv = MockBukkit.getMock().createInventory(null, ConfirmLook.STANDARD.rows() * 9);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theTwoDecisionsAreTheTwoColoursAndNotTheOtherWayRound() {
        populate();

        assertThat(inv.getItem(ConfirmLook.STANDARD.yesSlot())).isNotNull();
        assertThat(inv.getItem(ConfirmLook.STANDARD.yesSlot()).getType()).isEqualTo(Material.LIME_WOOL);
        assertThat(inv.getItem(ConfirmLook.STANDARD.noSlot())).isNotNull();
        assertThat(inv.getItem(ConfirmLook.STANDARD.noSlot()).getType()).isEqualTo(Material.RED_WOOL);
    }

    /** The standard's slots sit inside the window its rows ask for, and apart from each other. */
    @Test
    void bothButtonsFitTheWindowTheRowCountAsksFor() {
        ConfirmLook look = ConfirmLook.STANDARD;
        assertThat(look.yesSlot()).isBetween(0, look.rows() * 9 - 1);
        assertThat(look.noSlot()).isBetween(0, look.rows() * 9 - 1);
        assertThat(look.yesSlot()).isNotEqualTo(look.noSlot());
    }

    /** Each button is named with the word it was handed and nothing else, so no block name reaches the reader. */
    @Test
    void eachButtonCarriesTheWordItWasHanded() {
        populate();

        assertThat(nameAt(ConfirmLook.STANDARD.yesSlot())).isEqualTo("confirm");
        assertThat(nameAt(ConfirmLook.STANDARD.noSlot())).isEqualTo("cancel");
        assertThat(inv.getItem(ConfirmLook.STANDARD.yesSlot()).getItemMeta().hasLore())
                .isFalse();
    }

    /** Only the two slots are written, so a caller that decorated the window keeps whatever else it put there. */
    @Test
    void nothingButTheTwoSlotsIsTouched() {
        inv.setItem(0, new ItemStack(Material.DIAMOND));

        populate();

        assertThat(inv.getItem(0)).isNotNull();
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot != 0 && slot != ConfirmLook.STANDARD.yesSlot() && slot != ConfirmLook.STANDARD.noSlot()) {
                assertThat(inv.getItem(slot)).as("slot " + slot).isNull();
            }
        }
    }

    private void populate() {
        new ConfirmRenderer().populate(inv, ConfirmLook.STANDARD, Component.text("confirm"), Component.text("cancel"));
    }

    private String nameAt(int slot) {
        ItemStack button = inv.getItem(slot);
        assertThat(button).isNotNull();
        assertThat(button.getItemMeta().displayName()).isNotNull();
        return PlainTextComponentSerializer.plainText()
                .serialize(button.getItemMeta().displayName());
    }
}
