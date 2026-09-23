package com.uxplima.uxmlib.hook.edit;

import java.util.BitSet;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.nbt.FaweCompoundTag;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinDoubleTag;
import org.enginehub.linbus.tree.LinListTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;
import org.jspecify.annotations.Nullable;

/**
 * One edit's boundary, riding in FastAsyncWorldEdit's queue.
 *
 * <p>FAWE writes a chunk at a time, and every chunk an edit writes passes through the processors of that edit
 * before it reaches the world. This is one of them: it asks the rule about every cell the chunk would write and
 * leaves the refused ones unwritten, which in FAWE's layout means putting its reserved id back in the cell. It
 * does the same for the entities the chunk would create or remove, the tiles it would write and the biomes it
 * would paint, because each of those changes the world at a position just as a block does.
 *
 * <p>It runs on FAWE's worker threads, several at once for one edit, which is what {@link EditBoundary} already
 * says a rule has to bear.
 */
final class BoundedChunks implements IBatchProcessor {

    /** The cell value FAWE reads as "this edit does not write here". */
    private static final char UNWRITTEN = (char) BlockTypesCache.ReservedIDs.__RESERVED__;

    private static final int SECTION = 4096;
    private static final int BIOME_CELLS = 64;

    private final EditBoundary.Edit edit;
    private final EditBoundary rule;
    private final IntFunction<String> blockIds;

    /** Whether this edit has already been cut short, so the consumer is told once and not per cell. */
    private final AtomicBoolean told = new AtomicBoolean();

    BoundedChunks(EditBoundary.Edit edit, EditBoundary rule, IntFunction<String> blockIds) {
        this.edit = Objects.requireNonNull(edit, "edit");
        this.rule = Objects.requireNonNull(rule, "rule");
        this.blockIds = Objects.requireNonNull(blockIds, "blockIds");
    }

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        BitSet refused = blocks(set, baseX, baseZ);
        tiles(set, baseX, baseZ, refused);
        biomes(set, baseX, baseZ);
        entities(set);
        removals(get, set);
        return set;
    }

    /**
     * An extent this processor was asked to stand in front of instead of a queue.
     *
     * <p>FAWE asks that only of an extent that holds no processors, and an edit written straight into one would
     * never pass through this. {@link FaweEditGuard} cancels such an edit rather than let it through unasked, so
     * what is handed back here is an extent that writes nothing.
     */
    @Override
    public Extent construct(Extent child) {
        return new NullExtent();
    }

    /** After the processors that add or change blocks, so the cells this reads are the ones about to be written. */
    @Override
    public ProcessorScope getScope() {
        return ProcessorScope.REMOVING_BLOCKS;
    }

    /** The cells refused, numbered from the lowest section up, so a tile can tell its block was one of them. */
    private BitSet blocks(IChunkSet set, int baseX, int baseZ) {
        BitSet refused = new BitSet();
        int minLayer = set.getMinSectionPosition();
        for (int layer = minLayer; layer <= set.getMaxSectionPosition(); layer++) {
            char[] cells = set.hasSection(layer) ? set.loadIfPresent(layer) : null;
            if (cells == null) {
                continue;
            }
            boolean trimmed = false;
            int baseY = layer << 4;
            for (int at = 0; at < SECTION && at < cells.length; at++) {
                char cell = cells[at];
                if (cell == UNWRITTEN) {
                    continue;
                }
                int x = baseX + (at & 15);
                int y = baseY + (at >> 8);
                int z = baseZ + ((at >> 4) & 15);
                if (cutShort(rule.mayWrite(edit, x, y, z, blockIds.apply(cell)))) {
                    cells[at] = UNWRITTEN;
                    refused.set((layer - minLayer) * SECTION + at);
                    trimmed = true;
                }
            }
            if (trimmed) {
                set.setBlocks(layer, cells);
            }
        }
        return refused;
    }

    /**
     * A tile goes with its block: one whose block was refused, or that sits where the edit may not change
     * anything, is dropped. Its key holds x and z inside the chunk and y as it is.
     */
    private void tiles(IChunkSet set, int baseX, int baseZ, BitSet refused) {
        Map<BlockVector3, FaweCompoundTag> tiles = set.tiles();
        if (tiles.isEmpty()) {
            return;
        }
        int minLayer = set.getMinSectionPosition();
        tiles.keySet().removeIf(at -> {
            int cell = ((at.y() >> 4) - minLayer) * SECTION + ((at.y() & 15) << 8 | (at.z() & 15) << 4 | (at.x() & 15));
            return refused.get(cell) || cutShort(rule.mayChange(edit, baseX + at.x(), at.y(), baseZ + at.z()));
        });
    }

    /** A biome cell covers four blocks each way, and it is asked about at its lowest corner. */
    private void biomes(IChunkSet set, int baseX, int baseZ) {
        @Nullable BiomeType[][] biomes = set.getBiomes();
        if (biomes == null) {
            return;
        }
        int minLayer = set.getMinSectionPosition();
        for (int index = 0; index < biomes.length; index++) {
            @Nullable BiomeType[] cells = biomes[index];
            if (cells == null) {
                continue;
            }
            int baseY = (minLayer + index) << 4;
            for (int at = 0; at < BIOME_CELLS && at < cells.length; at++) {
                if (cells[at] == null) {
                    continue;
                }
                int x = baseX + ((at & 3) << 2);
                int y = baseY + (((at >> 4) & 3) << 2);
                int z = baseZ + (((at >> 2) & 3) << 2);
                if (cutShort(rule.mayChange(edit, x, y, z))) {
                    cells[at] = null;
                }
            }
        }
    }

    private void entities(IChunkSet set) {
        Collection<FaweCompoundTag> entities = set.entities();
        if (entities.isEmpty()) {
            return;
        }
        entities.removeIf(entity -> {
            LinCompoundTag tag = entity.linTag();
            LinStringTag id = tag.findTag("Id", LinTagType.stringTag());
            int[] at = blockOf(tag);
            if (id == null || at == null) {
                // FAWE does not spawn an entity it cannot place or name, so there is nothing to ask about.
                return false;
            }
            return cutShort(rule.mayCreate(edit, at[0], at[1], at[2], id.value()));
        });
    }

    /** An entity the edit removes is asked about where it stands now, when the chunk can say. */
    private void removals(IChunkGet get, IChunkSet set) {
        Set<UUID> removes = set.getEntityRemoves();
        if (removes == null || removes.isEmpty()) {
            return;
        }
        removes.removeIf(uuid -> {
            FaweCompoundTag standing = get.entity(uuid);
            int[] at = standing == null ? null : blockOf(standing.linTag());
            return at != null && cutShort(rule.mayChange(edit, at[0], at[1], at[2]));
        });
    }

    private static int @Nullable [] blockOf(LinCompoundTag tag) {
        LinListTag<LinDoubleTag> pos = tag.findListTag("Pos", LinTagType.doubleTag());
        if (pos == null || pos.value().size() < 3) {
            return null;
        }
        return new int[] {
            (int) Math.floor(pos.get(0).valueAsDouble()),
            (int) Math.floor(pos.get(1).valueAsDouble()),
            (int) Math.floor(pos.get(2).valueAsDouble())
        };
    }

    /** Take the rule's answer, and tell the consumer the first time it is no. */
    private boolean cutShort(boolean allowed) {
        if (allowed) {
            return false;
        }
        if (told.compareAndSet(false, true)) {
            rule.refused(edit);
        }
        return true;
    }
}
