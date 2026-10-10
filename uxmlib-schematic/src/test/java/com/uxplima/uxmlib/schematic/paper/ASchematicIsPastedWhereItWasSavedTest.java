package com.uxplima.uxmlib.schematic.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.schematic.PaletteIndices;
import com.uxplima.uxmlib.schematic.Schematic;
import com.uxplima.uxmlib.schematic.SchematicBiomes;
import com.uxplima.uxmlib.schematic.SchematicBlockEntity;
import com.uxplima.uxmlib.schematic.SchematicEntity;
import com.uxplima.uxmlib.schematic.Vec3i;
import com.uxplima.uxmlib.schematic.nbt.NbtCompound;
import com.uxplima.uxmlib.schematic.nbt.NbtList;
import com.uxplima.uxmlib.schematic.nbt.NbtTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * A schematic lands where it was saved around, turned as asked, a chunk at a time and a slice a tick, and
 * the report says what it could not place as saved.
 */
class ASchematicIsPastedWhereItWasSavedTest {

    private static final int DATA_VERSION = 4671;

    private ServerMock server;
    private WorldMock world;
    private TickScheduler scheduler;
    private MockServerAccess access;
    private SchematicPaster paster;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("paste");
        scheduler = new TickScheduler();
        access = new MockServerAccess();
        paster = new SchematicPaster(scheduler, new BlockStates(), access);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Each block lands at the paste point plus the offset it was saved with")
    void blocksLandAtThePointPlusTheOffset() {
        Schematic schematic = Schematic.builder(3, 2, 2)
                .offset(new Vec3i(-1, 0, -1))
                .dataVersion(DATA_VERSION)
                .block(0, 0, 0, "minecraft:stone")
                .block(2, 1, 1, "minecraft:dirt")
                .build();

        PasteReport report = pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT);

        assertThat(world.getBlockAt(9, 5, 9).getType()).isEqualTo(Material.STONE);
        assertThat(world.getBlockAt(11, 6, 10).getType()).isEqualTo(Material.DIRT);
        assertThat(report.blocksPlaced()).isEqualTo(2);
        assertThat(report.faithful()).isTrue();
    }

    @Test
    @DisplayName("Air leaves what stands unless told to clear it, and a structure void never clears")
    void airAndStructureVoid() {
        Schematic schematic = Schematic.builder(2, 1, 1)
                .dataVersion(DATA_VERSION)
                .block(1, 0, 0, "minecraft:structure_void")
                .build();
        world.getBlockAt(10, 5, 10).setType(Material.GLASS);
        world.getBlockAt(11, 5, 10).setType(Material.GLASS);

        pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT);
        assertThat(world.getBlockAt(10, 5, 10).getType()).isEqualTo(Material.GLASS);

        PasteReport cleared = pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT.withPasteAir(true));
        assertThat(world.getBlockAt(10, 5, 10).getType()).isEqualTo(Material.AIR);
        assertThat(world.getBlockAt(11, 5, 10).getType()).isEqualTo(Material.GLASS);
        assertThat(cleared.blocksPlaced()).isEqualTo(1);
    }

    @Test
    @DisplayName("A quarter turn clockwise puts what was east of the point south of it, facing turned with it")
    void aQuarterTurnClockwise() {
        Schematic schematic = Schematic.builder(2, 1, 1)
                .dataVersion(DATA_VERSION)
                .block(0, 0, 0, "minecraft:stone")
                .block(1, 0, 0, "minecraft:furnace[facing=north,lit=false]")
                .build();

        pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_90));

        assertThat(world.getBlockAt(10, 5, 10).getType()).isEqualTo(Material.STONE);
        assertThat(world.getBlockAt(10, 5, 11).getType()).isEqualTo(Material.FURNACE);
        assertThat(((Directional) world.getBlockAt(10, 5, 11).getBlockData()).getFacing())
                .isEqualTo(BlockFace.EAST);
        assertThat(world.getBlockAt(11, 5, 10).getType()).isEqualTo(Material.AIR);
    }

    @Test
    @DisplayName("A half turn and a quarter turn back land where a turn about the point puts them")
    void theOtherTurns() {
        Schematic schematic = Schematic.builder(3, 1, 2)
                .offset(new Vec3i(-1, 0, 0))
                .dataVersion(DATA_VERSION)
                .block(2, 0, 1, "minecraft:stone")
                .build();
        // Saved one east and one south of the point.

        pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_180));
        assertThat(world.getBlockAt(9, 5, 9).getType()).isEqualTo(Material.STONE);

        pasted(schematic, at(20, 5, 20), PasteOptions.DEFAULT.withRotation(Rotation.COUNTERCLOCKWISE_90));
        assertThat(world.getBlockAt(21, 5, 19).getType()).isEqualTo(Material.STONE);

        pasted(schematic, at(30, 5, 30), PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_90));
        assertThat(world.getBlockAt(29, 5, 31).getType()).isEqualTo(Material.STONE);
    }

    @Test
    @DisplayName("A paste larger than a tick's share spreads over ticks, a slice a tick")
    void aLargePasteSpreadsOverTicks() {
        Schematic.Builder builder = Schematic.builder(4, 1, 4).dataVersion(DATA_VERSION);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                builder.block(x, 0, z, "minecraft:stone");
            }
        }

        CompletableFuture<PasteReport> paste = paster.paste(
                builder.build(),
                at(0, 5, 0),
                PasteOptions.DEFAULT.withBlocksPerTick(4).withConcurrency(1));

        assertThat(paste).isNotDone();
        assertThat(placedStone(0, 5, 0, 4)).isEqualTo(4);
        assertThat(scheduler.runOut()).isEqualTo(3);
        assertThat(paste.join().blocksPlaced()).isEqualTo(16);
        assertThat(placedStone(0, 5, 0, 4)).isEqualTo(16);
    }

    @Test
    @DisplayName("Cancelling a paste stops it after the slice in hand")
    void cancellingStops() {
        Schematic.Builder builder = Schematic.builder(4, 1, 4).dataVersion(DATA_VERSION);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                builder.block(x, 0, z, "minecraft:stone");
            }
        }

        CompletableFuture<PasteReport> paste = paster.paste(
                builder.build(),
                at(0, 5, 0),
                PasteOptions.DEFAULT.withBlocksPerTick(4).withConcurrency(1));
        paste.cancel(false);
        scheduler.runOut();

        assertThat(placedStone(0, 5, 0, 4)).isEqualTo(4);
    }

    @Test
    @DisplayName("A paste across chunks works every chunk, each loaded before it is worked")
    void acrossChunks() {
        Schematic.Builder builder = Schematic.builder(20, 1, 20).dataVersion(DATA_VERSION);
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                builder.block(x, 0, z, "minecraft:stone");
            }
        }

        PasteReport report = pasted(builder.build(), at(-5, 5, -5), PasteOptions.DEFAULT.withConcurrency(2));

        assertThat(report.blocksPlaced()).isEqualTo(400);
        assertThat(placedStone(-5, 5, -5, 20)).isEqualTo(400);
        assertThat(access.loaded).hasSize(4);
    }

    @Test
    @DisplayName("A state read as another and a state the server does not know are reported, the unknown left alone")
    void changedAndUnknownStatesAreReported() {
        Schematic schematic = Schematic.builder(2, 1, 1)
                .dataVersion(DATA_VERSION)
                .block(0, 0, 0, "minecraft:grass")
                .block(1, 0, 0, "minecraft:no_such_block")
                .build();
        world.getBlockAt(11, 5, 10).setType(Material.GLASS);

        PasteReport report = pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT);

        assertThat(world.getBlockAt(10, 5, 10).getType()).isEqualTo(Material.SHORT_GRASS);
        assertThat(world.getBlockAt(11, 5, 10).getType()).isEqualTo(Material.GLASS);
        assertThat(report.statesChanged()).containsKey("minecraft:grass");
        assertThat(report.statesUnknown()).containsExactly("minecraft:no_such_block");
        assertThat(report.faithful()).isFalse();
    }

    @Test
    @DisplayName("Blocks that would land above the world are counted and left out")
    void pastTheTopOfTheWorld() {
        Schematic schematic = Schematic.builder(1, 3, 1)
                .dataVersion(DATA_VERSION)
                .block(0, 0, 0, "minecraft:stone")
                .block(0, 1, 0, "minecraft:stone")
                .block(0, 2, 0, "minecraft:stone")
                .build();
        int top = world.getMaxHeight() - 2;

        PasteReport report = pasted(schematic, at(0, top, 0), PasteOptions.DEFAULT);

        assertThat(report.blocksPlaced()).isEqualTo(2);
        assertThat(report.blocksOutsideWorld()).isEqualTo(1);
        assertThat(world.getBlockAt(0, top + 1, 0).getType()).isEqualTo(Material.STONE);
    }

    @Test
    @DisplayName("A block entity is filled once its block stands, and a chest over a chest keeps nothing of the old")
    void blockEntitiesAreFilled() {
        world.getBlockAt(11, 5, 10).setType(Material.CHEST);
        Chest old = (Chest) world.getBlockAt(11, 5, 10).getState();
        old.getSnapshotInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
        old.update(true, false);
        Schematic schematic = Schematic.builder(2, 1, 1)
                .dataVersion(DATA_VERSION)
                .block(0, 0, 0, "minecraft:spawner")
                .block(1, 0, 0, "minecraft:chest[facing=north,type=single,waterlogged=false]")
                .blockEntity(new SchematicBlockEntity(
                        new Vec3i(0, 0, 0),
                        "minecraft:mob_spawner",
                        NbtCompound.builder()
                                .put(
                                        "SpawnData",
                                        NbtCompound.builder()
                                                .put(
                                                        "entity",
                                                        NbtCompound.builder()
                                                                .putString("id", "minecraft:zombie")
                                                                .build())
                                                .build())
                                .putShort("Delay", 7)
                                .build()))
                .blockEntity(new SchematicBlockEntity(
                        new Vec3i(1, 0, 0),
                        "minecraft:chest",
                        NbtCompound.builder()
                                .putString("CustomName", "{\"text\":\"Loot\"}")
                                .build()))
                .build();

        PasteReport report = pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT);

        CreatureSpawner spawner = (CreatureSpawner) world.getBlockAt(10, 5, 10).getState();
        assertThat(spawner.getSpawnedType()).isEqualTo(EntityType.ZOMBIE);
        assertThat(spawner.getDelay()).isEqualTo(7);
        Chest chest = (Chest) world.getBlockAt(11, 5, 10).getState();
        assertThat(chest.getSnapshotInventory().isEmpty()).isTrue();
        assertThat(chest.customName()).isNotNull();
        assertThat(report.blockEntitiesApplied()).isEqualTo(2);
        assertThat(report.blockEntitiesNotCarried()).isEmpty();
    }

    @Test
    @DisplayName("An entity lands turned with the paste, facing turned with it, after every block stands")
    void entitiesLandTurned() {
        Schematic schematic = Schematic.builder(3, 2, 3)
                .dataVersion(DATA_VERSION)
                .block(2, 0, 2, "minecraft:stone")
                .entity(new SchematicEntity(
                        0.5,
                        1,
                        1.5,
                        "minecraft:armor_stand",
                        NbtCompound.builder()
                                .put("Rotation", NbtList.of(List.of(new NbtTag.FloatTag(10f), new NbtTag.FloatTag(5f))))
                                .build()))
                .build();

        PasteReport report = pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_90));

        assertThat(access.made).hasSize(1);
        Location to = access.made.getFirst().at();
        assertThat(to.getX()).isCloseTo(9.5, within(1e-9));
        assertThat(to.getY()).isCloseTo(6, within(1e-9));
        assertThat(to.getZ()).isCloseTo(10.5, within(1e-9));
        assertThat(to.getYaw()).isCloseTo(100f, within(1e-4f));
        assertThat(to.getPitch()).isCloseTo(5f, within(1e-4f));
        assertThat(report.entitiesSpawned()).isEqualTo(1);
    }

    @Test
    @DisplayName("A hanging entity is made in the block it hangs in, named in whatever frame the file names it")
    void hangingEntitiesKeepTheirBlock() {
        Schematic schematic = Schematic.builder(4, 3, 4)
                .dataVersion(DATA_VERSION)
                .entity(new SchematicEntity(
                        2.5,
                        1.5,
                        2.96875,
                        "minecraft:item_frame",
                        NbtCompound.builder()
                                .putIntArray("block_pos", 100, 64, 100)
                                .putByte("Facing", 2)
                                .build()))
                .entity(new SchematicEntity(
                        1.0,
                        1.0,
                        0.03125,
                        "minecraft:painting",
                        NbtCompound.builder()
                                // Two blocks square, facing south: it stands on the line between its block
                                // and the one east of it, and between its block and the one above.
                                .putInt("TileX", -41)
                                .putInt("TileY", 70)
                                .putInt("TileZ", 7)
                                .putByte("facing", 0)
                                .build()))
                .entity(new SchematicEntity(
                        3.0,
                        1.5,
                        2.96875,
                        "minecraft:painting",
                        NbtCompound.builder()
                                // Two blocks wide, facing north: it stands on the line between its block and
                                // the one west of it.
                                .putIntArray("block_pos", 5, 1, 2)
                                .putByte("facing", 2)
                                .build()))
                .build();

        pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT);

        assertThat(access.made).hasSize(3);
        PasteMade frame = made("minecraft:item_frame", 0);
        assertThat(frame.data().intArray("block_pos"))
                .hasValueSatisfying(pos -> assertThat(pos).containsExactly(12, 6, 12));
        assertThat(frame.at().getBlockX()).isEqualTo(12);
        assertThat(frame.at().getZ()).isEqualTo(12.0);
        PasteMade south = made("minecraft:painting", 0);
        assertThat(south.data().intValue("TileX")).hasValue(10);
        assertThat(south.data().intValue("TileY")).hasValue(5);
        assertThat(south.data().intValue("TileZ")).hasValue(10);
        PasteMade north = made("minecraft:painting", 1);
        assertThat(north.data().intArray("block_pos"))
                .hasValueSatisfying(pos -> assertThat(pos).containsExactly(13, 6, 12));
    }

    @Test
    @DisplayName("A hanging entity turned with the paste hangs in the block its own turned to")
    void aTurnedHangingEntity() {
        Schematic schematic = Schematic.builder(4, 3, 4)
                .dataVersion(DATA_VERSION)
                .entity(new SchematicEntity(
                        2.5,
                        1.5,
                        2.96875,
                        "minecraft:item_frame",
                        NbtCompound.builder().putIntArray("block_pos", 2, 1, 2).build()))
                .build();

        pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_90));

        // The block two east and two south of the point lands two south and two west of it.
        assertThat(made("minecraft:item_frame", 0).data().intArray("block_pos"))
                .hasValueSatisfying(pos -> assertThat(pos).containsExactly(8, 6, 12));

        Schematic painting = Schematic.builder(2, 2, 1)
                .dataVersion(DATA_VERSION)
                .entity(new SchematicEntity(
                        1.0,
                        1.0,
                        0.03125,
                        "minecraft:painting",
                        NbtCompound.builder()
                                .putIntArray("block_pos", 0, 0, 0)
                                .putByte("facing", 0)
                                .build()))
                .build();

        pasted(painting, at(10, 5, 10), PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_180));

        // Its block, the point itself, stays the point; the painting now stands half a block west of it.
        assertThat(made("minecraft:painting", 0).data().intArray("block_pos"))
                .hasValueSatisfying(pos -> assertThat(pos).containsExactly(10, 5, 10));
    }

    @Test
    @DisplayName("Entities and biomes are carried only when asked")
    void entitiesAndBiomesOnlyWhenAsked() {
        PaletteIndices desert = PaletteIndices.of(1);
        Schematic schematic = Schematic.builder(1, 1, 1)
                .dataVersion(DATA_VERSION)
                .block(0, 0, 0, "minecraft:sand")
                .biomes(SchematicBiomes.perColumn(List.of("minecraft:desert"), desert))
                .entity(new SchematicEntity(0.5, 0, 0.5, "minecraft:armor_stand", NbtCompound.empty()))
                .build();
        Biome before = world.getBiome(10, 5, 10);

        pasted(
                schematic,
                at(10, 5, 10),
                PasteOptions.DEFAULT.withEntities(false).withBiomes(false));
        assertThat(access.made).isEmpty();
        assertThat(world.getBiome(10, 5, 10)).isEqualTo(before);

        pasted(schematic, at(10, 5, 10), PasteOptions.DEFAULT);
        assertThat(access.made).hasSize(1);
        assertThat(world.getBiome(10, 5, 10)).isEqualTo(Biome.DESERT);
    }

    private record PasteMade(NbtCompound data, Location at) {}

    private PasteMade made(String id, int nth) {
        return access.made.stream()
                .filter(made -> made.id().equals(id))
                .map(made -> new PasteMade(made.data(), made.at()))
                .skip(nth)
                .findFirst()
                .orElseThrow();
    }

    private PasteReport pasted(Schematic schematic, Location at, PasteOptions options) {
        CompletableFuture<PasteReport> paste = paster.paste(schematic, at, options);
        scheduler.runOut();
        assertThat(paste).isDone();
        return paste.join();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    private int placedStone(int x0, int y, int z0, int size) {
        int count = 0;
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                if (world.getBlockAt(x, y, z).getType() == Material.STONE) {
                    count++;
                }
            }
        }
        return count;
    }
}
