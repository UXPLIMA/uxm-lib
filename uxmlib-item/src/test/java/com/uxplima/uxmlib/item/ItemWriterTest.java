package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A file we write is a file we read.
 *
 * <p>{@link ItemConfig} has read the whole definition of an item since it was written, and nothing could
 * write one back, so every plugin that took an item out of a player's hand invented its own subset and every
 * subset was smaller than the reader understands. uxmCrates wrote seven fields, so a Sharpness V sword handed
 * to its reward editor became a plain sword with a glint on it and an enchanted book could not be expressed.
 *
 * <p>These tests are the round trip and nothing else: build a stack, write it, read it back, and check that
 * what came out is what went in. A key one half spells and the other does not fails here rather than on a
 * player's screen.
 */
class ItemWriterTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ConfigurationNode empty() {
        return HoconConfigurationLoader.builder().build().createNode();
    }

    /** Write {@code stack} and read it straight back, which is the only assertion that matters here. */
    private static ItemStack roundTrip(ItemStack stack) throws Exception {
        ConfigurationNode node = empty();
        ItemWriter.write(stack, node);
        return ItemConfig.load(node).build();
    }

    @Test
    @DisplayName("a plain stack writes a material and a count and nothing else")
    void aPlainStackIsShort() throws Exception {
        ConfigurationNode node = empty();
        ItemWriter.write(new ItemStack(Material.DIAMOND, 3), node);

        assertThat(node.node("material").getString()).isEqualTo("DIAMOND");
        assertThat(node.node("amount").getInt()).isEqualTo(3);
        assertThat(node.node("name").virtual()).isTrue();
        assertThat(node.node("enchants").virtual()).isTrue();
        assertThat(ItemWriter.isPlain(new ItemStack(Material.DIAMOND, 3))).isTrue();
    }

    /**
     * The one that matters. An enchanted sword is the most common reward on any survival server and it is
     * exactly what was being lost.
     */
    @Test
    @DisplayName("an enchanted, named, lored sword survives the round trip with its enchantments on it")
    void anEnchantedSwordSurvives() throws Exception {
        ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(Text.mini("<red>Winter's Edge"))
                .lore(List.of(Text.mini("<gray>Cold to the touch.")))
                .enchant(Enchantment.SHARPNESS, 5)
                .enchant(Enchantment.UNBREAKING, 3)
                .unbreakable(true)
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .build();

        ItemStack back = roundTrip(sword);

        assertThat(back.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(back.getEnchantmentLevel(Enchantment.SHARPNESS)).isEqualTo(5);
        assertThat(back.getEnchantmentLevel(Enchantment.UNBREAKING)).isEqualTo(3);
        assertThat(back.getItemMeta().isUnbreakable()).isTrue();
        assertThat(back.getItemMeta().getItemFlags()).contains(ItemFlag.HIDE_ATTRIBUTES);
        assertThat(Text.plain(back.getItemMeta().displayName())).isEqualTo("Winter's Edge");
        assertThat(back.getItemMeta().lore()).hasSize(1);
    }

    /**
     * An enchanted book stores its enchantments rather than carrying them, and the two are different things
     * to the client: a book with `enchants` glows and does nothing, a book with `stored-enchants` is the book
     * a player puts on an anvil. There was no way to write one down at all.
     */
    @Test
    @DisplayName("an enchanted book keeps what it stores, not what it carries")
    void anEnchantedBookKeepsItsStoredEnchants() throws Exception {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.MENDING, 1, true);
        meta.addStoredEnchant(Enchantment.FORTUNE, 3, true);
        book.setItemMeta(meta);

        ItemStack back = roundTrip(book);

        EnchantmentStorageMeta backMeta = (EnchantmentStorageMeta) back.getItemMeta();
        assertThat(backMeta.getStoredEnchantLevel(Enchantment.MENDING)).isEqualTo(1);
        assertThat(backMeta.getStoredEnchantLevel(Enchantment.FORTUNE)).isEqualTo(3);
    }

    @Test
    @DisplayName("a worn tool arrives as worn as it left")
    void damageSurvives() throws Exception {
        ItemStack pickaxe = ItemBuilder.of(Material.DIAMOND_PICKAXE).damage(742).build();

        ItemStack back = roundTrip(pickaxe);

        assertThat(((Damageable) back.getItemMeta()).getDamage()).isEqualTo(742);
    }

    @Test
    @DisplayName("a glint an operator asked for without an enchantment is still a glint")
    void aBareGlintSurvives() throws Exception {
        ItemStack paper = ItemBuilder.of(Material.PAPER).glow(true).build();

        ItemStack back = roundTrip(paper);

        assertThat(back.getItemMeta().hasEnchantmentGlintOverride()).isTrue();
        assertThat(back.getItemMeta().getEnchantmentGlintOverride()).isTrue();
    }

    @Test
    @DisplayName("an item that carries anything at all is not plain, so a caller knows to write the block")
    void anythingWrittenOnItIsNotPlain() {
        assertThat(ItemWriter.isPlain(ItemBuilder.of(Material.DIAMOND)
                        .enchant(Enchantment.SHARPNESS, 1)
                        .build()))
                .isFalse();
        assertThat(ItemWriter.isPlain(
                        ItemBuilder.of(Material.DIAMOND).name(Text.mini("x")).build()))
                .isFalse();
        assertThat(ItemWriter.isPlain(
                        ItemBuilder.of(Material.DIAMOND_PICKAXE).damage(1).build()))
                .isFalse();
    }
}
