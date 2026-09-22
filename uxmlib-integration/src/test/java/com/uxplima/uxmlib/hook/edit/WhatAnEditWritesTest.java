package com.uxplima.uxmlib.hook.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A boundary is told what an edit writes, not only where.
 *
 * <p>A rule that knows only the coordinates can keep a player inside their plot and cannot keep a command block
 * out of it. uxm-plots found that on 2026-09-23: a player allowed WorldEdit in their own plot could {@code //set
 * command_block} or paste a spawner, and the forbidden block list its block listener reads never saw it, because
 * an edit does not go through a Bukkit event and the seam never said which block it was writing.
 *
 * <p>The block and the entity come by the namespaced id the game writes, so a consumer compares them without
 * holding a WorldEdit type. A consumer that only cares where keeps writing {@code mayChange}: the two new questions
 * fall back to it.
 */
final class WhatAnEditWritesTest {

    private static final BlockVector3 AT = BlockVector3.at(3, 64, -7);

    @Test
    @DisplayName("a block is refused by what it is, before the editor writes it")
    void aBlockIsRefusedByWhatItIs() throws WorldEditException {
        List<String> told = new ArrayList<>();
        Extent world = mock(Extent.class);
        EditBoundary noCommandBlocks = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return true;
            }

            @Override
            public boolean mayWrite(Edit edit, int x, int y, int z, String block) {
                told.add(x + " " + y + " " + z + " " + block);
                return !block.equals("minecraft:command_block");
            }
        };
        WorldEditGuard.Bounded bounded = new WorldEditGuard.Bounded(world, edit(), noCommandBlocks);

        assertThat(bounded.setBlock(AT, state("minecraft:command_block"))).isFalse();

        assertThat(told).containsExactly("3 64 -7 minecraft:command_block");
        verify(world, never()).setBlock(any(BlockVector3.class), any(BlockState.class));
    }

    @Test
    @DisplayName("a block the rule allows reaches the world")
    void anAllowedBlockIsWritten() throws WorldEditException {
        Extent world = mock(Extent.class);
        BlockState stone = state("minecraft:stone");
        when(world.setBlock(AT, stone)).thenReturn(true);
        EditBoundary onlyStone = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return false;
            }

            @Override
            public boolean mayWrite(Edit edit, int x, int y, int z, String block) {
                return block.equals("minecraft:stone");
            }
        };

        assertThat(new WorldEditGuard.Bounded(world, edit(), onlyStone).setBlock(AT, stone))
                .isTrue();
        verify(world).setBlock(AT, stone);
    }

    @Test
    @DisplayName("an entity is refused by what it is, so a pasted command block minecart stays out")
    void anEntityIsRefusedByWhatItIs() {
        List<String> told = new ArrayList<>();
        Extent world = mock(Extent.class);
        EditBoundary noCarts = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return true;
            }

            @Override
            public boolean mayCreate(Edit edit, int x, int y, int z, String entity) {
                told.add(entity);
                return !entity.equals("minecraft:command_block_minecart");
            }
        };
        BaseEntity cart = new BaseEntity(new EntityType("minecraft:command_block_minecart"));
        Location there = new Location(world, 3.5, 64, -6.5);

        assertThat(new WorldEditGuard.Bounded(world, edit(), noCarts).createEntity(there, cart))
                .isNull();

        assertThat(told).containsExactly("minecraft:command_block_minecart");
        verify(world, never()).createEntity(any(Location.class), any(BaseEntity.class));
    }

    @Test
    @DisplayName("a rule that only asks where is still asked, for a block and for an entity")
    void aRuleThatOnlyAsksWhereStillHolds() throws WorldEditException {
        List<String> asked = new ArrayList<>();
        Extent world = mock(Extent.class);
        EditBoundary nowhere = (edit, x, y, z) -> {
            asked.add(x + " " + y + " " + z);
            return false;
        };
        WorldEditGuard.Bounded bounded = new WorldEditGuard.Bounded(world, edit(), nowhere);

        assertThat(bounded.setBlock(AT, state("minecraft:stone"))).isFalse();
        assertThat(bounded.createEntity(
                        new Location(world, 3.5, 64, -6.5), new BaseEntity(new EntityType("minecraft:pig"))))
                .isNull();

        assertThat(asked).containsExactly("3 64 -7", "3 64 -7");
    }

    @Test
    @DisplayName("an edit cut short by what it writes is told once, like one cut short by where")
    void aRefusedBlockIsToldOnce() throws WorldEditException {
        List<String> refused = new ArrayList<>();
        EditBoundary noSpawners = new EditBoundary() {
            @Override
            public boolean mayChange(Edit edit, int x, int y, int z) {
                return true;
            }

            @Override
            public boolean mayWrite(Edit edit, int x, int y, int z, String block) {
                return !block.equals("minecraft:spawner");
            }

            @Override
            public void refused(Edit edit) {
                refused.add("refused");
            }
        };
        WorldEditGuard.Bounded bounded = new WorldEditGuard.Bounded(mock(Extent.class), edit(), noSpawners);

        bounded.setBlock(AT, state("minecraft:spawner"));
        bounded.setBlock(AT.add(1, 0, 0), state("minecraft:spawner"));

        assertThat(refused).containsExactly("refused");
    }

    /** A block state that answers its type, which is all the boundary reads off it. */
    private static BlockState state(String id) {
        BlockState state = mock(BlockState.class);
        when(state.getBlockType()).thenReturn(new BlockType(id));
        return state;
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
}
