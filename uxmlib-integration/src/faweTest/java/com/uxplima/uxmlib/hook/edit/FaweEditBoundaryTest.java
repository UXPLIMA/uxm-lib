package com.uxplima.uxmlib.hook.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fastasyncworldedit.core.nbt.FaweCompoundTag;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinDoubleTag;
import org.enginehub.linbus.tree.LinFloatTag;
import org.enginehub.linbus.tree.LinListTag;
import org.enginehub.linbus.tree.LinTagType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The edit boundary under FastAsyncWorldEdit, which does not honour the extent WorldEdit's seam hands a listener.
 *
 * <p>FAWE throws a listener's extent away unless its own config names the class, and its fast paths write a chunk
 * at a time past any extent it keeps. uxm-plots ran its edit boundary on that seam, so on a FAWE server a player
 * could {@code //set} across a neighbour's plot and over a frozen contest entry, and nothing was asked. What FAWE
 * honours is a processor: every chunk an edit writes passes through the processors of that edit first. These
 * tests drive that processor over one chunk, the way FAWE hands it one.
 *
 * <p>A chunk section holds 4096 cells, indexed {@code y << 8 | z << 4 | x} inside the section, and a cell holding
 * FAWE's reserved id is one the edit leaves alone. A tile is keyed by its x and z inside the chunk and its real y.
 * An entity carries its real position. A biome cell covers four blocks each way.
 */
final class FaweEditBoundaryTest {

    private static final char RESERVED = 0;
    private static final char STONE = 2;
    private static final char COMMAND_BLOCK = 3;

    /** Chunk (2, -1) spans x 32 to 47 and z -16 to -1. */
    private static final int CHUNK_X = 2;

    private static final int CHUNK_Z = -1;

    @Test
    @DisplayName("a block outside the boundary is left unwritten, asked at its real position")
    void aBlockOutsideIsLeftUnwritten() {
        FakeChunk chunk = new FakeChunk();
        chunk.block(33, -12, -15, STONE);
        chunk.block(40, -12, -15, STONE);
        List<String> asked = new ArrayList<>();
        EditBoundary westOf40 = (edit, x, y, z) -> {
            asked.add(x + " " + y + " " + z);
            return x < 40;
        };

        processed(westOf40, chunk);

        assertThat(asked).containsExactlyInAnyOrder("33 -12 -15", "40 -12 -15");
        assertThat(chunk.at(33, -12, -15)).isEqualTo(STONE);
        assertThat(chunk.at(40, -12, -15)).isEqualTo(RESERVED);
    }

    @Test
    @DisplayName("a block is left unwritten by what it is, told by the id the game writes")
    void aBlockIsRefusedByWhatItIs() {
        FakeChunk chunk = new FakeChunk();
        chunk.block(34, 70, -2, COMMAND_BLOCK);
        chunk.block(35, 70, -2, STONE);
        List<String> told = new ArrayList<>();

        processed(new NoCommandBlocks(told), chunk);

        assertThat(told).containsExactlyInAnyOrder("minecraft:command_block", "minecraft:stone");
        assertThat(chunk.at(34, 70, -2)).isEqualTo(RESERVED);
        assertThat(chunk.at(35, 70, -2)).isEqualTo(STONE);
    }

    @Test
    @DisplayName("a cell the edit does not write is not asked about")
    void anUnwrittenCellIsNotAsked() {
        FakeChunk chunk = new FakeChunk();
        chunk.block(33, 5, -3, STONE);
        List<String> asked = new ArrayList<>();

        processed(
                (edit, x, y, z) -> {
                    asked.add(x + " " + y + " " + z);
                    return true;
                },
                chunk);

        assertThat(asked).containsExactly("33 5 -3");
    }

    @Test
    @DisplayName("an entity outside the boundary, or one the rule refuses by kind, is not created")
    void anEntityIsAskedAboutWhereAndWhat() {
        FakeChunk chunk = new FakeChunk();
        chunk.entities.add(entity("minecraft:pig", 33.5, 64, -14.5));
        chunk.entities.add(entity("minecraft:command_block_minecart", 34.5, 64, -14.5));
        chunk.entities.add(entity("minecraft:cow", 45.5, 64, -14.5));
        EditBoundary rule = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return x < 40;
            }

            @Override
            public boolean mayCreate(Edit edit, int x, int y, int z, String entity) {
                return mayChange(edit, x, y, z) && !entity.equals("minecraft:command_block_minecart");
            }
        };

        processed(rule, chunk);

        assertThat(chunk.entities).extracting(FaweEditBoundaryTest::idOf).containsExactly("minecraft:pig");
    }

    @Test
    @DisplayName("a tile is dropped with the block it belongs to, and wherever the boundary ends")
    void aTileFollowsItsBlock() {
        FakeChunk chunk = new FakeChunk();
        chunk.block(34, 70, -2, COMMAND_BLOCK);
        chunk.tiles.put(BlockVector3.at(34 - 32, 70, -2 + 16), tile());
        chunk.tiles.put(BlockVector3.at(35 - 32, 70, -2 + 16), tile());
        chunk.tiles.put(BlockVector3.at(44 - 32, 70, -2 + 16), tile());
        EditBoundary rule = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return x < 40;
            }

            @Override
            public boolean mayWrite(Edit edit, int x, int y, int z, String block) {
                return mayChange(edit, x, y, z) && !block.equals("minecraft:command_block");
            }
        };

        processed(rule, chunk);

        assertThat(chunk.tiles.keySet()).containsExactly(BlockVector3.at(3, 70, 14));
    }

    @Test
    @DisplayName("a biome cell outside the boundary is left as it is")
    void aBiomeOutsideIsLeft() {
        FakeChunk chunk = new FakeChunk();
        BiomeType desert = new BiomeType("minecraft:desert");
        chunk.biome(33, 64, -15, desert);
        chunk.biome(45, 64, -15, desert);

        processed((edit, x, y, z) -> x < 40, chunk);

        assertThat(chunk.biomeAt(33, 64, -15)).isSameAs(desert);
        assertThat(chunk.biomeAt(45, 64, -15)).isNull();
    }

    @Test
    @DisplayName("an entity outside the boundary is not removed")
    void anEntityOutsideIsNotRemoved() {
        FakeChunk chunk = new FakeChunk();
        UUID inside = UUID.nameUUIDFromBytes("inside".getBytes(StandardCharsets.UTF_8));
        UUID outside = UUID.nameUUIDFromBytes("outside".getBytes(StandardCharsets.UTF_8));
        chunk.removes.add(inside);
        chunk.removes.add(outside);
        chunk.standing.put(inside, entity("minecraft:pig", 33.5, 64, -14.5));
        chunk.standing.put(outside, entity("minecraft:pig", 45.5, 64, -14.5));

        processed((edit, x, y, z) -> x < 40, chunk);

        assertThat(chunk.removes).containsExactly(inside);
    }

    @Test
    @DisplayName("an edit cut short is told once however many cells it loses")
    void anEditIsToldOnce() {
        FakeChunk chunk = new FakeChunk();
        chunk.block(40, 1, -1, STONE);
        chunk.block(41, 1, -1, STONE);
        chunk.entities.add(entity("minecraft:pig", 45.5, 64, -14.5));
        List<String> refused = new ArrayList<>();
        EditBoundary rule = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return false;
            }

            @Override
            public void refused(Edit edit) {
                refused.add("refused");
            }
        };

        processed(rule, chunk);

        assertThat(refused).containsExactly("refused");
    }

    @Test
    @DisplayName("a player's edit carries the boundary into FAWE's queue before anything is written")
    void aPlayersEditCarriesTheBoundary() {
        Extent queue = mock(Extent.class);
        List<IBatchProcessor> added = new ArrayList<>();
        when(queue.addProcessor(any())).thenAnswer(call -> {
            added.add(call.getArgument(0));
            return queue;
        });
        EditSessionEvent event = event(EditSession.Stage.BEFORE_CHANGE, queue);

        FaweEditGuard.guard(event, (edit, x, y, z) -> true);

        assertThat(added).hasSize(1);
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("an edit the boundary cannot ride along with is cancelled, never let through unasked")
    void anEditThatCannotBeGuardedIsCancelled() {
        Extent world = mock(Extent.class);
        when(world.addProcessor(any())).thenReturn(mock(Extent.class));
        List<String> refused = new ArrayList<>();
        EditBoundary rule = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return true;
            }

            @Override
            public void refused(Edit edit) {
                refused.add("refused");
            }
        };
        EditSessionEvent event = event(EditSession.Stage.BEFORE_CHANGE, world);

        FaweEditGuard.guard(event, rule);

        assertThat(event.isCancelled()).isTrue();
        assertThat(refused).containsExactly("refused");
    }

    @Test
    @DisplayName("the console, another plugin and every other stage are left alone")
    void onlyAPlayersChangeIsGuarded() {
        Extent queue = mock(Extent.class);
        EditSessionEvent console = new EditSessionEvent(mock(World.class), null, -1, EditSession.Stage.BEFORE_CHANGE);
        console.setExtent(queue);
        EditSessionEvent history = event(EditSession.Stage.BEFORE_HISTORY, queue);

        FaweEditGuard.guard(console, (edit, x, y, z) -> false);
        FaweEditGuard.guard(history, (edit, x, y, z) -> false);

        assertThat(console.isCancelled()).isFalse();
        assertThat(history.isCancelled()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(queue);
    }

    @Test
    @DisplayName("the WorldEdit half finds the FAWE guard by name, so a FAWE server is guarded by FAWE's way in")
    void theFaweGuardIsFoundByName() {
        assertThat(WorldEditGuard.faweGuard()).containsInstanceOf(FaweEditGuard.class);
    }

    private static void processed(EditBoundary rule, FakeChunk chunk) {
        BoundedChunks processor = new BoundedChunks(edit(), rule, FaweEditBoundaryTest::blockId);
        IChunk at = mock(IChunk.class);
        when(at.getX()).thenReturn(CHUNK_X);
        when(at.getZ()).thenReturn(CHUNK_Z);
        IChunkGet get = mock(IChunkGet.class);
        when(get.entity(any())).thenAnswer(call -> chunk.standing.get(call.<UUID>getArgument(0)));
        processor.processSet(at, get, chunk.set());
    }

    /** The ids the test chunk writes, in place of FAWE's block cache, which needs a running server. */
    private static String blockId(int id) {
        return switch (id) {
            case STONE -> "minecraft:stone";
            case COMMAND_BLOCK -> "minecraft:command_block";
            default -> "minecraft:air";
        };
    }

    private static EditSessionEvent event(EditSession.Stage stage, Extent extent) {
        Actor player = mock(Actor.class);
        when(player.isPlayer()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes("somebody".getBytes(StandardCharsets.UTF_8)));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        EditSessionEvent event = new EditSessionEvent(world, player, -1, stage);
        event.setExtent(extent);
        return event;
    }

    private static FaweCompoundTag entity(String id, double x, double y, double z) {
        return FaweCompoundTag.of(LinCompoundTag.builder()
                .putString("Id", id)
                .put(
                        "Pos",
                        LinListTag.of(
                                LinTagType.doubleTag(),
                                List.of(LinDoubleTag.of(x), LinDoubleTag.of(y), LinDoubleTag.of(z))))
                .put("Rotation", LinListTag.of(LinTagType.floatTag(), List.of(LinFloatTag.of(0), LinFloatTag.of(0))))
                .build());
    }

    private static FaweCompoundTag tile() {
        return FaweCompoundTag.of(
                LinCompoundTag.builder().putString("Command", "op somebody").build());
    }

    private static String idOf(FaweCompoundTag entity) {
        return entity.linTag().getTag("Id", LinTagType.stringTag()).value();
    }

    private static EditBoundary.Edit edit() {
        return new EditBoundary.Edit() {

            @Override
            public UUID player() {
                return UUID.nameUUIDFromBytes("somebody".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String world() {
                return "world";
            }

            @Override
            public boolean holds(String permission) {
                return false;
            }
        };
    }

    /** A rule that keeps command blocks out and records every block it is told about. */
    private record NoCommandBlocks(List<String> told) implements EditBoundary {

        @Override
        public boolean mayChange(Edit edit, int x, int y, int z) {
            return true;
        }

        @Override
        public boolean mayWrite(Edit edit, int x, int y, int z, String block) {
            told.add(block);
            return !block.equals("minecraft:command_block");
        }
    }

    /** One chunk of an edit, laid out the way FAWE lays out what it is about to write. */
    private static final class FakeChunk {

        private static final int MIN_SECTION = -4;
        private static final int MAX_SECTION = 19;

        private final Map<Integer, char[]> sections = new HashMap<>();
        private final BiomeType[][] biomes = new BiomeType[MAX_SECTION - MIN_SECTION + 1][];
        private final Set<FaweCompoundTag> entities = new HashSet<>();
        private final Map<BlockVector3, FaweCompoundTag> tiles = new HashMap<>();
        private final Set<UUID> removes = new HashSet<>();
        private final Map<UUID, FaweCompoundTag> standing = new HashMap<>();

        void block(int x, int y, int z, char id) {
            sections.computeIfAbsent(y >> 4, layer -> new char[4096])[index(x, y, z)] = id;
        }

        char at(int x, int y, int z) {
            return java.util.Objects.requireNonNull(sections.get(y >> 4))[index(x, y, z)];
        }

        void biome(int x, int y, int z, BiomeType biome) {
            int layer = (y >> 4) - MIN_SECTION;
            if (biomes[layer] == null) {
                biomes[layer] = new BiomeType[64];
            }
            biomes[layer][biomeIndex(x, y, z)] = biome;
        }

        BiomeType biomeAt(int x, int y, int z) {
            return biomes[(y >> 4) - MIN_SECTION][biomeIndex(x, y, z)];
        }

        IChunkSet set() {
            IChunkSet set = mock(IChunkSet.class);
            when(set.getMinSectionPosition()).thenReturn(MIN_SECTION);
            when(set.getMaxSectionPosition()).thenReturn(MAX_SECTION);
            when(set.hasSection(org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(call -> sections.containsKey(call.<Integer>getArgument(0)));
            when(set.loadIfPresent(org.mockito.ArgumentMatchers.anyInt()))
                    .thenAnswer(call -> sections.get(call.<Integer>getArgument(0)));
            when(set.getBiomes()).thenReturn(biomes);
            when(set.entities()).thenReturn(entities);
            when(set.tiles()).thenReturn(tiles);
            when(set.getEntityRemoves()).thenReturn(removes);
            return set;
        }

        private static int index(int x, int y, int z) {
            return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        }

        private static int biomeIndex(int x, int y, int z) {
            return (y & 12) << 2 | (z & 12) | (x & 12) >> 2;
        }
    }
}
