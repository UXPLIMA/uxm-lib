package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * An item file may name a custom item.
 *
 * <p>The gap four analyses named, in four plugins, under the same words. A file that wrote
 * {@code material = "oraxen:ruby"} failed at load with "unknown material", and the operator's only
 * remaining option was to write the block it is drawn as, which is a different item. One hook here closes
 * it in every plugin of ours that reads an item out of a file, which is all of them.
 *
 * <p>The rest of the spec still applies on top, and that is the half worth guarding: a file that takes a
 * vendor's item and renames it has to get the vendor's item with the new name, not one or the other.
 */
class ItemConfigCustomItemTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        ItemConfig.forgetItemSource();
    }

    @Test
    @DisplayName("with no source a namespaced material is still an unknown material, as it always was")
    void withNoSourceItIsStillUnknown() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ItemConfig.load(node("material = \"oraxen:ruby\"")))
                .withMessageContaining("oraxen:ruby");
    }

    @Test
    @DisplayName("a source that owns the id decides the item")
    void asourceThatOwnsTheIdDecidesTheItem() {
        ItemConfig.itemsFrom(id -> "oraxen:ruby".equals(id) ? Optional.of(ruby()) : Optional.empty());

        ItemStack made = ItemConfig.load(node("material = \"oraxen:ruby\"")).build();

        assertThat(made.getType()).isEqualTo(Material.EMERALD);
        assertThat(made.getEnchantmentLevel(Enchantment.UNBREAKING))
                .describedAs("the vendor's own item, not a lookalike")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("everything else in the spec is applied on top, so a file may rename a vendor's item")
    void therestOfTheSpecStillApplies() {
        ItemConfig.itemsFrom(id -> Optional.of(ruby()));

        ItemStack made = ItemConfig.load(node("""
                        material = "oraxen:ruby"
                        amount   = 5
                        name     = "<gold>The Ruby"
                        lore     = ["<gray>Theirs, renamed by us"]
                        """)).build();

        assertThat(made.getType()).isEqualTo(Material.EMERALD);
        assertThat(made.getAmount()).isEqualTo(5);
        assertThat(made.getItemMeta().hasDisplayName()).isTrue();
        assertThat(made.getItemMeta().lore()).hasSize(1);
        assertThat(made.getEnchantmentLevel(Enchantment.UNBREAKING)).isEqualTo(3);
    }

    @Test
    @DisplayName("a source that does not own the id falls through, so a typo is still a load error")
    void anunownedIdFallsThrough() {
        ItemConfig.itemsFrom(id -> Optional.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> ItemConfig.load(node("material = \"nexo:ruby\"")));
    }

    @Test
    @DisplayName("a minecraft namespace is the server's own, so the source is never asked about one")
    void theMinecraftNamespaceIsNotACustomItem() {
        ItemConfig.itemsFrom(id -> {
            throw new IllegalStateException("the source must not be asked about a vanilla id");
        });

        assertThat(ItemConfig.load(node("material = \"minecraft:diamond\""))
                        .build()
                        .getType())
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    @DisplayName("an ordinary material never reaches the source either")
    void anordinaryMaterialNeverReachesTheSource() {
        ItemConfig.itemsFrom(id -> {
            throw new IllegalStateException("the source must not be asked about a plain material");
        });

        assertThat(ItemConfig.load(node("material = DIAMOND")).build().getType())
                .isEqualTo(Material.DIAMOND);
    }

    private static ItemStack ruby() {
        ItemStack stack = new ItemStack(Material.EMERALD);
        stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return stack;
    }

    private static ConfigurationNode node(String hocon) {
        try {
            return HoconConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(hocon)))
                    .build()
                    .load();
        } catch (org.spongepowered.configurate.ConfigurateException broken) {
            throw new IllegalStateException(broken);
        }
    }
}
