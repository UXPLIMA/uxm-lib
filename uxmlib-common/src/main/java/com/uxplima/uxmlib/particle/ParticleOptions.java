package com.uxplima.uxmlib.particle;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A particle plus exactly the extra data that particle requires, as a sealed set of records. Modelling the
 * payload per kind means the compiler rejects "DUST without a colour" or "FLAME with a block" at the call
 * site, replacing CMILib-style untyped {@code Object} casts. {@link #particle()} names the particle and
 * {@link #data()} is the payload handed to {@code spawnParticle} (or {@code null} when the particle takes
 * none); {@link Particles} reads these two to pick the matching overload.
 */
public sealed interface ParticleOptions
        permits ParticleOptions.Plain,
                ParticleOptions.Dust,
                ParticleOptions.DustTransition,
                ParticleOptions.Block,
                ParticleOptions.Item,
                ParticleOptions.Written {

    /** The particle to spawn. */
    Particle particle();

    /** The extra data {@code spawnParticle} needs for this particle, or {@code null} if it takes none. */
    @Nullable Object data();

    /** A particle that carries no extra data, such as {@link Particle#FLAME} or {@link Particle#HEART}. */
    static Plain of(Particle particle) {
        return new Plain(particle);
    }

    /** Coloured {@link Particle#DUST} of the given {@link Color} and size (1.0 is the default scale). */
    static Dust dust(Color color, float size) {
        return new Dust(color, size);
    }

    /** {@link Particle#DUST_COLOR_TRANSITION} fading from one colour to another at the given size. */
    static DustTransition dustTransition(Color from, Color to, float size) {
        return new DustTransition(from, to, size);
    }

    /** A block-textured particle (e.g. {@link Particle#BLOCK}, {@code BLOCK_MARKER}, {@code FALLING_DUST}). */
    static Block block(Particle particle, BlockData blockData) {
        return new Block(particle, blockData);
    }

    /** An item-textured particle ({@link Particle#ITEM}). */
    static Item item(ItemStack item) {
        return new Item(item);
    }

    /**
     * The particle an operator named, the way they write it: any case, spaces around it, {@code happy_villager} for
     * {@link Particle#HAPPY_VILLAGER}. Empty for a name the server does not know.
     */
    static Optional<Particle> named(String written) {
        Objects.requireNonNull(written, "written");
        String name = written.strip();
        for (Particle particle : Particle.values()) {
            if (particle.name().equalsIgnoreCase(name)) {
                return Optional.of(particle);
            }
        }
        return Optional.empty();
    }

    /**
     * {@code particle} with the data an operator wrote beside its name, placed at {@code at} where the data needs a
     * place. A particle that needs no data is {@link Plain} and the word is ignored. For the rest the word is a colour
     * {@code #ff0000}, two colours {@code #ff0000>#0000ff} for a transition, a block or an item by name, or a number,
     * and a blank word reads as the particle's default. Empty when the word cannot be read, so a caller draws nothing
     * rather than having the server refuse the particle.
     */
    static Optional<ParticleOptions> read(Particle particle, String written, Location at) {
        Objects.requireNonNull(particle, "particle");
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(at, "at");
        if (particle.getDataType() == Void.class) {
            return Optional.of(of(particle));
        }
        return ParticleData.read(particle, written, at).map(data -> new Written(particle, data));
    }

    /**
     * A particle written as one value, its name then its data: {@code heart}, {@code dust #ff0000},
     * {@code block oak_log}. What a plugin reads from a setting that names a particle. Empty when the name or the data
     * cannot be read.
     */
    static Optional<ParticleOptions> parse(String written, Location at) {
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(at, "at");
        String value = written.strip();
        int space = value.indexOf(' ');
        String name = space < 0 ? value : value.substring(0, space);
        String data = space < 0 ? "" : value.substring(space + 1);
        return named(name).flatMap(particle -> read(particle, data, at));
    }

    /** A particle that needs no extra data. */
    record Plain(Particle particle) implements ParticleOptions {
        public Plain {
            Objects.requireNonNull(particle, "particle");
            ParticleData.requireDataType(particle, Void.class);
        }

        @Override
        public @Nullable Object data() {
            return null;
        }
    }

    /** {@link Particle#DUST} with a colour and a size. */
    record Dust(Color color, float size) implements ParticleOptions {
        public Dust {
            Objects.requireNonNull(color, "color");
            ParticleData.requirePositiveSize(size);
        }

        @Override
        public Particle particle() {
            return Particle.DUST;
        }

        @Override
        public Particle.DustOptions data() {
            return new Particle.DustOptions(color, size);
        }
    }

    /** {@link Particle#DUST_COLOR_TRANSITION} fading from one colour to another. */
    record DustTransition(Color from, Color to, float size) implements ParticleOptions {
        public DustTransition {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            ParticleData.requirePositiveSize(size);
        }

        @Override
        public Particle particle() {
            return Particle.DUST_COLOR_TRANSITION;
        }

        @Override
        public Particle.DustTransition data() {
            return new Particle.DustTransition(from, to, size);
        }
    }

    /** A block-data particle; the explicit {@code particle} lets one record serve every block-textured kind. */
    record Block(Particle particle, BlockData blockData) implements ParticleOptions {
        public Block {
            Objects.requireNonNull(particle, "particle");
            Objects.requireNonNull(blockData, "blockData");
            ParticleData.requireDataType(particle, BlockData.class);
        }

        @Override
        public BlockData data() {
            return blockData;
        }
    }

    /**
     * Any particle with data of the class it declares, read from a word an operator wrote: what {@link #read} answers
     * for a particle the typed records above do not cover, such as a spell, a trail, a vibration or a geyser.
     */
    record Written(Particle particle, Object data) implements ParticleOptions {
        public Written {
            Objects.requireNonNull(particle, "particle");
            Objects.requireNonNull(data, "data");
            if (!particle.getDataType().isInstance(data)) {
                throw new IllegalArgumentException("particle " + particle + " expects data of type "
                        + particle.getDataType().getSimpleName() + ", not "
                        + data.getClass().getSimpleName());
            }
        }
    }

    /** An {@link Particle#ITEM} particle textured from an {@link ItemStack}. */
    record Item(ItemStack item) implements ParticleOptions {
        public Item {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public Particle particle() {
            return Particle.ITEM;
        }

        @Override
        public ItemStack data() {
            return item;
        }
    }
}
