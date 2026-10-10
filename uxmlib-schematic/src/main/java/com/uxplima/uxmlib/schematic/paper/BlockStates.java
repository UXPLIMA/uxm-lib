package com.uxplima.uxmlib.schematic.paper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import org.jspecify.annotations.Nullable;

/**
 * Turns the block states a schematic names into block data this server knows.
 *
 * <p>A file is written with the names of the game that wrote it. A name this server knows is taken as it is.
 * A name the game later changed is carried to its current one: grass is short grass since 1.20.3, a grass
 * path a dirt path since 1.17, a chain an iron chain since 1.21.9, a sign an oak sign since 1.14. Two names
 * kept their spelling and changed their meaning, so they are read by the version that wrote them: a stone
 * slab before 1.14 is what is now a smooth stone slab, and a wall's sides before 1.16 were true or false
 * where they are now low or none. A state whose properties this server no longer takes keeps the ones it
 * does, and a block nobody knows is left out and named in the report.
 */
public final class BlockStates {

    /** 1.14: the stone slab became the smooth stone slab, and a new stone slab took its name. */
    static final int VILLAGE_AND_PILLAGE = 1952;

    /** 1.16: a wall's sides went from true and false to low, tall and none. */
    static final int NETHER_UPDATE = 2566;

    /** Names that changed, from the old to the current, tried when the old one is not known. */
    private static final Map<String, String> RENAMED = Map.of(
            "minecraft:grass", "minecraft:short_grass",
            "minecraft:grass_path", "minecraft:dirt_path",
            "minecraft:chain", "minecraft:iron_chain",
            "minecraft:sign", "minecraft:oak_sign",
            "minecraft:wall_sign", "minecraft:oak_wall_sign");

    private static final List<String> WALL_SIDES = List.of("north", "east", "south", "west");

    private final Function<String, BlockData> parser;

    /** Resolves through the server's own parser. */
    public BlockStates() {
        this(Bukkit::createBlockData);
    }

    BlockStates(Function<String, BlockData> parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    /** What a palette resolves to: block data by palette index, and what had to be read as something else. */
    public static final class Resolved {

        private final @Nullable BlockData[] data;
        private final Map<String, String> changed;
        private final List<String> unknown;

        Resolved(@Nullable BlockData[] data, Map<String, String> changed, List<String> unknown) {
            this.data = data.clone();
            this.changed = Map.copyOf(changed);
            this.unknown = List.copyOf(unknown);
        }

        /** The block data of palette index {@code index}, or nothing for a block this server does not know. */
        public @Nullable BlockData at(int index) {
            BlockData found = data[index];
            return found == null ? null : found.clone();
        }

        public int size() {
            return data.length;
        }

        /** Every state that was read as another, and what it was read as. */
        public Map<String, String> changed() {
            return changed;
        }

        /** Every state that could not be read at all. */
        public List<String> unknown() {
            return unknown;
        }
    }

    public Resolved resolve(List<String> palette, int dataVersion) {
        BlockData[] data = new BlockData[palette.size()];
        Map<String, String> changed = new LinkedHashMap<>();
        List<String> unknown = new java.util.ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            String state = palette.get(i);
            BlockData found = resolve(state, dataVersion);
            data[i] = found;
            if (found == null) {
                unknown.add(state);
            } else if (!found.getAsString().equals(state) && !sameState(state, found)) {
                changed.put(state, found.getAsString());
            }
        }
        return new Resolved(data, changed, unknown);
    }

    /** The block data {@code state} is read as, or nothing when no reading of it is known here. */
    public @Nullable BlockData resolve(String state, int dataVersion) {
        String written = upgradeMeaning(state, dataVersion);
        BlockData exact = parse(written);
        if (exact != null) {
            return exact;
        }
        String block = blockOf(written);
        String renamed = RENAMED.get(block);
        String properties = written.substring(block.length());
        if (renamed != null) {
            BlockData carried = parse(renamed + properties);
            if (carried != null) {
                return carried;
            }
            return lenient(renamed, properties);
        }
        return lenient(block, properties);
    }

    /** The block alone, then each property this server still takes, one by one. */
    private @Nullable BlockData lenient(String block, String properties) {
        BlockData base = parse(block);
        if (base == null || properties.length() < 2) {
            return base;
        }
        for (String pair : properties.substring(1, properties.length() - 1).split(",", -1)) {
            BlockData one = parse(block + "[" + pair + "]");
            if (one != null) {
                base = base.merge(one);
            }
        }
        return base;
    }

    private @Nullable BlockData parse(String state) {
        try {
            return parser.apply(state);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The two names that kept their spelling and changed their meaning, read by the version that wrote them. */
    static String upgradeMeaning(String state, int dataVersion) {
        String block = blockOf(state);
        if (dataVersion < VILLAGE_AND_PILLAGE && block.equals("minecraft:stone_slab")) {
            return "minecraft:smooth_stone_slab" + state.substring(block.length());
        }
        if (dataVersion < NETHER_UPDATE && block.endsWith("_wall") && state.length() > block.length()) {
            String properties = state.substring(block.length() + 1, state.length() - 1);
            StringBuilder upgraded = new StringBuilder(block).append('[');
            String[] pairs = properties.split(",", -1);
            for (int i = 0; i < pairs.length; i++) {
                String[] pair = pairs[i].split("=", 2);
                String value = pair.length == 2 ? pair[1] : "";
                if (pair.length == 2 && WALL_SIDES.contains(pair[0])) {
                    value = value.equals("true") ? "low" : value.equals("false") ? "none" : value;
                }
                upgraded.append(i == 0 ? "" : ",").append(pair[0]);
                if (pair.length == 2) {
                    upgraded.append('=').append(value);
                }
            }
            return upgraded.append(']').toString();
        }
        return state;
    }

    static String blockOf(String state) {
        int properties = state.indexOf('[');
        String block = properties < 0 ? state : state.substring(0, properties);
        return block.contains(":") ? block : "minecraft:" + block;
    }

    /** Whether the server wrote back the same state, its properties perhaps in another order. */
    private static boolean sameState(String written, BlockData found) {
        return blockOf(written).equals(blockOf(found.getAsString()))
                && properties(written).equals(properties(found.getAsString()));
    }

    private static java.util.Set<String> properties(String state) {
        int open = state.indexOf('[');
        if (open < 0) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(java.util.Arrays.asList(
                state.substring(open + 1, state.length() - 1).split(",")));
    }
}
