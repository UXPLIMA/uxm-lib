package com.uxplima.uxmlib.schematic.paper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a paste did, and what it could not do.
 *
 * @param blocksPlaced the blocks set
 * @param blocksOutsideWorld the blocks above or below the world's height, left out
 * @param blockEntitiesApplied the block entities whose fields were carried
 * @param blockEntitiesNotCarried the kinds of block entity placed with their defaults, because this kind's
 *     fields are not carried
 * @param entitiesSpawned the entities made
 * @param entitiesRefused the kinds of entity the server could not make from what was saved
 * @param statesChanged every block state read as another, and what it was read as
 * @param statesUnknown every block state this server does not know, whose positions were left as they were
 */
public record PasteReport(
        long blocksPlaced,
        long blocksOutsideWorld,
        int blockEntitiesApplied,
        Set<String> blockEntitiesNotCarried,
        int entitiesSpawned,
        Set<String> entitiesRefused,
        Map<String, String> statesChanged,
        List<String> statesUnknown) {

    public PasteReport {
        blockEntitiesNotCarried = Set.copyOf(blockEntitiesNotCarried);
        entitiesRefused = Set.copyOf(entitiesRefused);
        statesChanged = Map.copyOf(statesChanged);
        statesUnknown = List.copyOf(statesUnknown);
    }

    /** Whether everything saved was placed as it was saved. */
    public boolean faithful() {
        return blocksOutsideWorld == 0
                && blockEntitiesNotCarried.isEmpty()
                && entitiesRefused.isEmpty()
                && statesChanged.isEmpty()
                && statesUnknown.isEmpty();
    }
}
