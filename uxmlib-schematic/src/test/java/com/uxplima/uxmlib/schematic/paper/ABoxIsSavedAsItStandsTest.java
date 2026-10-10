package com.uxplima.uxmlib.schematic.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.Vec3i;
import com.uxplima.uxmlib.schematic.format.SpongeSchematicReader;
import com.uxplima.uxmlib.schematic.format.SpongeSchematicWriter;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** A box of a world is saved as it stands, around the point it was saved from, and pastes back the same. */
class ABoxIsSavedAsItStandsTest {

    private ServerMock server;
    private WorldMock world;
    private TickScheduler scheduler;
    private MockServerAccess access;
    private SchematicCapture capture;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("capture");
        scheduler = new TickScheduler();
        access = new MockServerAccess();
        capture = new SchematicCapture(scheduler, access);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("A box is saved block by block, its corner kept as a step from the point it was saved around")
    void aBoxIsSaved() {
        world.getBlockAt(2, 10, 3).setType(Material.STONE);
        world.getBlockAt(4, 11, 5).setType(Material.OAK_PLANKS);

        Schematic saved =
                captured(new Vec3i(4, 11, 5), new Vec3i(2, 10, 3), new Vec3i(3, 10, 3), CaptureOptions.DEFAULT);

        assertThat(saved.width()).isEqualTo(3);
        assertThat(saved.height()).isEqualTo(2);
        assertThat(saved.length()).isEqualTo(3);
        assertThat(saved.offset()).isEqualTo(new Vec3i(-1, 0, 0));
        assertThat(saved.blockAt(0, 0, 0)).isEqualTo("minecraft:stone");
        assertThat(saved.blockAt(2, 1, 2)).isEqualTo("minecraft:oak_planks");
        assertThat(saved.blockAt(1, 1, 1)).isEqualTo(Schematic.AIR);
        assertThat(saved.palette()).hasSize(3).first().isEqualTo(Schematic.AIR);
        assertThat(saved.dataVersion()).isPositive();
        assertThat(scheduler.asyncHops)
                .describedAs("blocks are read off the server's threads")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("A box across chunks is saved whole")
    void acrossChunks() {
        // Kept to positive chunks: MockBukkit's snapshot keys a block by x % 16, which is negative west of zero.
        world.getBlockAt(13, 10, 13).setType(Material.STONE);
        world.getBlockAt(33, 10, 33).setType(Material.DIRT);

        Schematic saved = captured(
                new Vec3i(13, 10, 13),
                new Vec3i(33, 10, 33),
                new Vec3i(16, 10, 16),
                CaptureOptions.DEFAULT.withConcurrency(2));

        assertThat(saved.width()).isEqualTo(21);
        assertThat(saved.blockAt(0, 0, 0)).isEqualTo("minecraft:stone");
        assertThat(saved.blockAt(20, 0, 20)).isEqualTo("minecraft:dirt");
        assertThat(access.loaded).hasSize(9);
    }

    @Test
    @DisplayName("What is saved writes to a file, reads back and pastes into the same blocks elsewhere")
    void aSavedBoxPastesBack() throws IOException {
        world.getBlockAt(0, 10, 0).setType(Material.STONE);
        world.getBlockAt(1, 10, 0).setType(Material.SPAWNER);
        CreatureSpawner spawner = (CreatureSpawner) world.getBlockAt(1, 10, 0).getState();
        spawner.setSpawnedType(EntityType.ZOMBIE);
        spawner.update(true, false);
        world.getBlockAt(1, 11, 1).setType(Material.GLASS);
        world.getBlockAt(3, 10, 0).setType(Material.SPAWNER);

        Schematic saved =
                captured(new Vec3i(0, 10, 0), new Vec3i(1, 11, 1), new Vec3i(0, 10, 0), CaptureOptions.DEFAULT);
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        SpongeSchematicWriter.write(saved, file);
        Schematic read = SpongeSchematicReader.withDefaults().read(new ByteArrayInputStream(file.toByteArray()));

        assertThat(read.blockEntities()).singleElement().satisfies(entity -> {
            assertThat(entity.pos()).isEqualTo(new Vec3i(1, 0, 0));
            assertThat(entity.id()).isEqualTo("minecraft:mob_spawner");
        });
        CompletableFuture<PasteReport> paste = new SchematicPaster(scheduler, new BlockStates(), access)
                .paste(read, new Location(world, 40, 20, 40), PasteOptions.DEFAULT);
        scheduler.runOut();
        assertThat(paste.join().faithful()).isTrue();
        assertThat(world.getBlockAt(40, 20, 40).getType()).isEqualTo(Material.STONE);
        assertThat(world.getBlockAt(41, 21, 41).getType()).isEqualTo(Material.GLASS);
        assertThat(((CreatureSpawner) world.getBlockAt(41, 20, 40).getState()).getSpawnedType())
                .isEqualTo(EntityType.ZOMBIE);
    }

    @Test
    @DisplayName("Entities standing in the box are saved, players never, and only when asked")
    void entitiesButNeverPlayers() {
        world.getBlockAt(0, 10, 0).setType(Material.STONE);
        ArmorStand stand = world.spawn(new Location(world, 1.25, 11, 1.75), ArmorStand.class);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 1.5, 11, 0.5));

        Schematic with =
                captured(new Vec3i(1, 10, 0), new Vec3i(3, 13, 3), new Vec3i(0, 10, 0), CaptureOptions.DEFAULT);
        Schematic without = captured(
                new Vec3i(1, 10, 0),
                new Vec3i(3, 13, 3),
                new Vec3i(0, 10, 0),
                CaptureOptions.DEFAULT.withEntities(false));

        assertThat(with.entities()).singleElement().satisfies(entity -> {
            assertThat(entity.id()).isEqualTo("minecraft:armor_stand");
            assertThat(entity.x()).describedAs("from the box's corner").isCloseTo(0.25, within(1e-9));
            assertThat(entity.y()).isCloseTo(1, within(1e-9));
            assertThat(entity.z()).isCloseTo(1.75, within(1e-9));
            assertThat(entity.data().string("CustomName")).hasValue("\"" + stand.getName() + "\"");
        });
        assertThat(without.entities()).isEmpty();
    }

    @Test
    @DisplayName("Biomes are saved only when asked")
    void biomesOnlyWhenAsked() {
        world.setBiome(0, 10, 0, Biome.DESERT);

        Schematic plain =
                captured(new Vec3i(0, 10, 0), new Vec3i(0, 10, 0), new Vec3i(0, 10, 0), CaptureOptions.DEFAULT);
        Schematic withBiomes = captured(
                new Vec3i(0, 10, 0), new Vec3i(0, 10, 0), new Vec3i(0, 10, 0), CaptureOptions.DEFAULT.withBiomes(true));

        assertThat(plain.biomes()).isEmpty();
        assertThat(withBiomes.biomes())
                .hasValueSatisfying(biomes -> assertThat(biomes.palette()).containsExactly("minecraft:desert"));
    }

    @Test
    @DisplayName("Metadata given is kept with what is saved")
    void metadataIsKept() {
        NbtCompound metadata = NbtCompound.builder().putString("Name", "island").build();

        Schematic saved = captured(
                new Vec3i(0, 10, 0),
                new Vec3i(0, 10, 0),
                new Vec3i(0, 10, 0),
                CaptureOptions.DEFAULT.withMetadata(metadata));

        assertThat(saved.metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName(
            "A box larger than allowed, or wholly outside the world's height, is refused before anything is read, and one partly outside is cut")
    void refusedBoxes() {
        CompletableFuture<Schematic> large = capture.capture(
                world, new Vec3i(0, 0, 0), new Vec3i(9, 9, 9), Vec3i.ZERO, CaptureOptions.DEFAULT.withMaxVolume(999));
        CompletableFuture<Schematic> above =
                capture.capture(world, new Vec3i(0, 500, 0), new Vec3i(1, 501, 1), Vec3i.ZERO, CaptureOptions.DEFAULT);

        assertThat(large).isCompletedExceptionally();
        assertThat(above).isCompletedExceptionally();
        assertThat(access.loaded).isEmpty();
        assertThat(captured(new Vec3i(0, -5, 0), new Vec3i(0, 1, 0), Vec3i.ZERO, CaptureOptions.DEFAULT)
                        .height())
                .describedAs("a box reaching below the world is cut at its floor")
                .isEqualTo(2);
        assertThat(capture.capture(
                        world,
                        new Vec3i(0, 0, 0),
                        new Vec3i(9, 9, 9),
                        Vec3i.ZERO,
                        CaptureOptions.DEFAULT.withMaxVolume(1000)))
                .isCompleted();
    }

    private Schematic captured(Vec3i corner, Vec3i other, Vec3i origin, CaptureOptions options) {
        CompletableFuture<Schematic> saved = capture.capture(world, corner, other, origin, options);
        assertThat(saved).isDone();
        try {
            return saved.join();
        } catch (CompletionException e) {
            throw new AssertionError("the capture failed", e.getCause());
        }
    }
}
