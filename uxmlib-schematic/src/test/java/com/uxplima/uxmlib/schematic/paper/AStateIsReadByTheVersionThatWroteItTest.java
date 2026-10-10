package com.uxplima.uxmlib.schematic.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A block state, and a piece of text, is read as the version that wrote it meant it: a name the game later
 * gave to another block is read as the block it was then, and a name the game since changed is read as the
 * block that took it over.
 */
class AStateIsReadByTheVersionThatWroteItTest {

    private static final int BEFORE_1_14 = 1631;
    private static final int BEFORE_1_16 = 2230;
    private static final int NOW = 4671;

    private final BlockStates states = new BlockStates();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("A stone slab written before 1.14 is the smooth stone slab, and after it the stone slab")
    void theStoneSlabByItsVersion() {
        assertThat(material("minecraft:stone_slab[type=bottom,waterlogged=false]", BEFORE_1_14))
                .isEqualTo(Material.SMOOTH_STONE_SLAB);
        assertThat(material("minecraft:stone_slab[type=bottom,waterlogged=false]", NOW))
                .isEqualTo(Material.STONE_SLAB);
    }

    @Test
    @DisplayName("A wall's sides written as true and false before 1.16 are read as low and none")
    void wallSidesByTheirVersion() {
        assertThat(BlockStates.upgradeMeaning(
                        "minecraft:cobblestone_wall[east=true,north=false,south=true,up=true,waterlogged=false,west=false]",
                        BEFORE_1_16))
                .isEqualTo(
                        "minecraft:cobblestone_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]");
        assertThat(BlockStates.upgradeMeaning("minecraft:cobblestone_wall[east=low]", NOW))
                .isEqualTo("minecraft:cobblestone_wall[east=low]");
    }

    @Test
    @DisplayName("A name the game since changed is read as the block that took it over, its properties kept")
    void renamedBlocks() {
        assertThat(material("minecraft:grass", NOW)).isEqualTo(Material.SHORT_GRASS);
        assertThat(material("minecraft:grass_path", NOW)).isEqualTo(Material.DIRT_PATH);
        BlockData sign = known("minecraft:sign[rotation=4,waterlogged=false]", NOW);
        assertThat(sign.getMaterial()).isEqualTo(Material.OAK_SIGN);
        assertThat(sign.getAsString()).contains("rotation=4");
    }

    @Test
    @DisplayName("A property this server no longer takes is dropped and the rest are kept")
    void anUnknownPropertyIsDropped() {
        BlockData stairs = known("minecraft:oak_stairs[facing=east,no_such_property=1]", NOW);

        assertThat(stairs.getMaterial()).isEqualTo(Material.OAK_STAIRS);
        assertThat(stairs.getAsString()).contains("facing=east");
    }

    @Test
    @DisplayName("A palette resolves by index, and says what it read as something else and what it could not read")
    void aPaletteResolves() {
        BlockStates.Resolved resolved = states.resolve(
                List.of("minecraft:air", "minecraft:stone", "minecraft:grass", "minecraft:no_such_block"), NOW);

        assertThat(resolved.size()).isEqualTo(4);
        assertThat(resolved.at(1)).extracting(BlockData::getMaterial).isEqualTo(Material.STONE);
        assertThat(resolved.at(3)).isNull();
        assertThat(resolved.changed()).containsOnlyKeys("minecraft:grass");
        assertThat(resolved.unknown()).containsExactly("minecraft:no_such_block");
        assertThat(resolved.at(1)).describedAs("a copy, so a paste may turn it").isNotSameAs(resolved.at(1));
    }

    @Test
    @DisplayName("Text written before 1.21.5 is a JSON string, and after it a tag tree or a plain string")
    void textByItsVersion() {
        Component red = Component.text("Loot", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);

        assertThat(TextNbt.read(
                        new NbtTag.StringTag("{\"text\":\"Loot\",\"color\":\"red\",\"bold\":true,\"italic\":false}"),
                        4000))
                .isEqualTo(red);
        assertThat(TextNbt.read(new NbtTag.StringTag("{\"text\":\"Loot\"}"), NOW))
                .describedAs("a string is plain text once text is tags")
                .isEqualTo(Component.text("{\"text\":\"Loot\"}"));
        NbtTag written = TextNbt.write(red);
        assertThat(written).isInstanceOf(NbtCompound.class);
        assertThat(((NbtCompound) written).get("bold")).isEqualTo(new NbtTag.ByteTag((byte) 1));
        assertThat(((NbtCompound) written).get("italic")).isEqualTo(new NbtTag.ByteTag((byte) 0));
        assertThat(TextNbt.read(written, NOW)).isEqualTo(red);
        assertThat(TextNbt.read(new NbtTag.StringTag("{not json"), 4000)).isEqualTo(Component.text("{not json"));
    }

    private Material material(String state, int dataVersion) {
        return known(state, dataVersion).getMaterial();
    }

    private BlockData known(String state, int dataVersion) {
        return Objects.requireNonNull(states.resolve(state, dataVersion), () -> state + " was not read");
    }
}
