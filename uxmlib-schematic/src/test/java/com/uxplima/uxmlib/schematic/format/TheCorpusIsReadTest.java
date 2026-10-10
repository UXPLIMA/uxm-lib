package com.uxplima.uxmlib.schematic.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.Vec3i;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Files real tools wrote are read, and the ones this reader does not take are refused by name.
 *
 * <p>Run with {@code ./gradlew :uxmlib-schematic:corpusTest -Pcorpus=<folder>}. The folder holds UXPLIMA's
 * own spawn, written by WorldEdit as Sponge version 2 at 1.20.1, and one bought village exported by its
 * maker at every game version from 1.8 to 1.21. The same village read from two versions of the format must
 * be the same village: that is a test against an implementation nobody here wrote.
 */
@Tag("corpus")
class TheCorpusIsReadTest {

    private static final Path CORPUS = Path.of(System.getProperty("uxmlib.schematic.corpus", "missing"));

    @Test
    @DisplayName("The UXPLIMA spawn, Sponge 2 from WorldEdit at 1.20.1, reads at its size with its contents")
    void theSpawnReads() throws IOException {
        Schematic spawn = read("uxplima-spawn-sponge-v2-1.20.1.schem");

        assertThat(spawn.width()).isEqualTo(363);
        assertThat(spawn.height()).isEqualTo(192);
        assertThat(spawn.length()).isEqualTo(394);
        assertThat(spawn.dataVersion()).isEqualTo(3463);
        assertThat(spawn.palette()).hasSize(935);
        assertThat(spawn.blockEntities()).hasSize(1444);
        assertThat(spawn.blockEntities())
                .describedAs("a block entity's fields, without its position, which a paste writes anew")
                .allSatisfy(entity ->
                        assertThat(entity.data().entries().keySet()).doesNotContain("x", "y", "z", "Pos", "Data"));
        assertThat(spawn.blockEntities())
                .describedAs("the fields WorldEdit put under Data in a version 2 file")
                .anySatisfy(entity -> assertThat(entity.data().entries()).isNotEmpty());
        assertThat(spawn.offset())
                .describedAs("the point WorldEdit copied around, not the absolute corner it also wrote")
                .isNotEqualTo(new Vec3i(-822, 59, -3432));
        assertWithin(spawn);
    }

    @Test
    @DisplayName("The village exported at 1.13.2 and at 1.21 is the same village, block by block")
    void theVillageIsTheSameAtTwoVersions() throws IOException {
        Schematic old = read("lobby_blue_village_1.13.2.schem");
        Schematic current = read("lobby_blue_village_1.21.schem");

        for (Schematic village : new Schematic[] {old, current}) {
            assertThat(village.width()).isEqualTo(168);
            assertThat(village.height()).isEqualTo(384);
            assertThat(village.length()).isEqualTo(160);
            assertWithin(village);
        }
        assertThat(current.blockEntities()).hasSameSizeAs(old.blockEntities());
        // Position by position the two files are one village. The game renamed grass to short grass in
        // 1.20.3, and the maker lit the later export with 690 light blocks and swapped eleven glowstones
        // for shroomlights. Anything else differing would be this reader misplacing a block.
        Map<String, Integer> changes = new TreeMap<>();
        for (int i = 0; i < old.volume(); i++) {
            String was = block(old.palette().get(old.paletteIndexAt(i)));
            String is = block(current.palette().get(current.paletteIndexAt(i)));
            if (!was.equals(is)) {
                changes.merge(was + " -> " + is, 1, Integer::sum);
            }
        }
        assertThat(changes)
                .containsOnlyKeys(
                        "minecraft:air -> minecraft:light",
                        "minecraft:glowstone -> minecraft:shroomlight",
                        "minecraft:grass -> minecraft:short_grass")
                .containsEntry("minecraft:air -> minecraft:light", 690)
                .containsEntry("minecraft:glowstone -> minecraft:shroomlight", 11);
    }

    @Test
    @DisplayName("A version this reader does not take, and an MCEdit file from before 1.13, are refused by name")
    void theOthersAreRefusedByName() {
        assertThatThrownBy(() -> read("sponge-v9-invented.schem"))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining("version 9");
        assertThatThrownBy(() -> read("lobby_blue_village_1.12.schematic"))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining("before Minecraft 1.13");
    }

    private static Schematic read(String name) throws IOException {
        Path file = CORPUS.resolve(name);
        assertThat(file).describedAs("the corpus file " + name).exists();
        try (InputStream in = Files.newInputStream(file)) {
            return SpongeSchematicReader.withDefaults().read(in);
        }
    }

    /** A state's block, without its properties. */
    private static String block(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static void assertWithin(Schematic schematic) {
        for (SchematicBlockEntity entity : schematic.blockEntities()) {
            assertThat(schematic.contains(entity.pos())).isTrue();
        }
        for (SchematicEntity entity : schematic.entities()) {
            assertThat(entity.x()).isBetween(-1.0, schematic.width() + 1.0);
            assertThat(entity.y()).isBetween(-1.0, schematic.height() + 1.0);
            assertThat(entity.z()).isBetween(-1.0, schematic.length() + 1.0);
        }
    }
}
