package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * An item put into a column comes back out of it.
 *
 * <p>The round trip through the node is {@link ItemWriterTest}'s subject. This is the string on either side
 * of it, and the two failures a database column brings that a file does not: a row nobody can parse, and a
 * row that means "no item".
 */
class ItemTextTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an enchanted, named tool survives the trip through a column")
    void theRoundTripKeepsWhatMatters() {
        ItemStack given = new ItemStack(Material.DIAMOND_PICKAXE);
        given.editMeta(meta -> {
            meta.displayName(Text.mini("<gold>Diggy"));
            meta.addEnchant(Enchantment.FORTUNE, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        });

        ItemStack back = ItemText.read(ItemText.write(given)).orElseThrow();

        assertThat(back.getType()).isEqualTo(Material.DIAMOND_PICKAXE);
        assertThat(back.getEnchantmentLevel(Enchantment.FORTUNE)).isEqualTo(3);
        assertThat(back.getEnchantmentLevel(Enchantment.UNBREAKING)).isEqualTo(2);
        assertThat(back.getItemMeta().hasDisplayName()).isTrue();
    }

    @Test
    @DisplayName("the text is HOCON an operator can read, not a blob")
    void theTextIsReadable() {
        ItemStack given = new ItemStack(Material.NETHERITE_AXE);
        given.editMeta(meta -> meta.addEnchant(Enchantment.EFFICIENCY, 5, true));

        String text = ItemText.write(given);

        assertThat(text).contains("NETHERITE_AXE");
        assertThat(text)
                .describedAs("an operator reading a table during an incident sees the enchantment")
                .containsIgnoringCase("efficiency");
    }

    @Test
    @DisplayName("an empty column is no item rather than an exception")
    void blankIsNothing() {
        assertThat(ItemText.read("")).isEmpty();
        assertThat(ItemText.read("   \n  ")).isEmpty();
    }

    @Test
    @DisplayName("a row nothing can parse is no item, because one bad row must not stop a load")
    void rubbishIsNothing() {
        assertThat(ItemText.read("{{{ this is not hocon")).isEmpty();
        assertThat(ItemText.read("material = NOT_A_REAL_MATERIAL")).isEmpty();
        assertThat(ItemText.read("amount = 3")).isEmpty();
    }

    @Test
    @DisplayName("an amount above one is kept, because a stack is a different thing from an item")
    void theAmountIsKept() {
        ItemStack given = new ItemStack(Material.OAK_LOG, 17);

        assertThat(ItemText.read(ItemText.write(given)).orElseThrow().getAmount())
                .isEqualTo(17);
    }
}
