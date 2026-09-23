package com.uxplima.uxmlib.common;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * The data a particle needs, read from a word an operator wrote.
 *
 * <p>The server refuses a dust, a block, an item, a spell and the rest drawn without data: on a Folia 26.2 server all
 * twenty two of them threw "missing required data class" when drawn with none. So wherever an operator names a
 * particle, the word beside it may carry the data: a colour written {@code #ff0000}, two of them {@code #ff0000>#0000ff} for a transition, a block or an item
 * by its name, or a number. A line that ends in none draws with a default: white, stone, or the number the particle
 * reads as ordinary. Data that cannot be read is no data at all, and the line draws nothing, the way a particle name
 * the server does not know draws nothing.
 *
 * <p>Every default is a constant and nothing here reads the world, because a particle line may run off the region
 * thread.
 */
public final class ParticleData {

    private static final int VIBRATION_TICKS = 20;

    private static final int TRAIL_TICKS = 20;

    private static final float SIZE = 1.0f;

    private ParticleData() {}

    /**
     * The data {@code particle} needs, read from {@code written} and placed at {@code at} where it needs a place;
     * empty when it cannot be read. Ask only for a particle that {@link #needs} data.
     */
    public static Optional<Object> of(Particle particle, String written, Location at) {
        String token = written.strip();
        Class<?> type = particle.getDataType();
        if (type == Color.class) {
            return colour(token).map(Object.class::cast);
        }
        if (type == Particle.DustOptions.class) {
            return colour(token).map(colour -> new Particle.DustOptions(colour, SIZE));
        }
        if (type == Particle.DustTransition.class) {
            return transition(token);
        }
        if (type == Particle.Spell.class) {
            return colour(token).map(colour -> new Particle.Spell(colour, SIZE));
        }
        if (type == Particle.Trail.class) {
            return colour(token).map(colour -> new Particle.Trail(above(at), colour, TRAIL_TICKS));
        }
        if (type == BlockData.class) {
            return material(token, Material::isBlock).map(Material::createBlockData);
        }
        if (type == ItemStack.class) {
            return material(token, Material::isItem).map(ItemStack::new);
        }
        return numbered(type, token, at);
    }

    /** Whether {@code particle} is refused without data. */
    public static boolean needs(Particle particle) {
        return particle.getDataType() != Void.class;
    }

    /**
     * Draw {@code particle} in {@code world} with the data {@code written} names, or its default when blank. Answers
     * false and draws nothing when the data cannot be read, so a caller can say which word was wrong.
     */
    public static boolean spawn(
            World world, Particle particle, Location at, int count, double spread, double speed, String written) {
        Object data = null;
        if (needs(particle)) {
            Optional<Object> read = of(particle, written, at);
            if (read.isEmpty()) {
                return false;
            }
            data = read.get();
        }
        world.spawnParticle(particle, at, count, spread, spread, spread, speed, data);
        return true;
    }

    private static Optional<Object> numbered(Class<?> type, String token, Location at) {
        if (type == Float.class) {
            return number(token, 1.0d).map(Double::floatValue);
        }
        if (type == Integer.class) {
            return number(token, 0.0d).map(Double::intValue);
        }
        if (type == Vibration.class) {
            return number(token, VIBRATION_TICKS)
                    .map(ticks ->
                            new Vibration(new Vibration.Destination.BlockDestination(above(at)), ticks.intValue()));
        }
        if (type == Particle.Geyser.class) {
            return number(token, 1.0d).map(blocks -> new Particle.Geyser(blocks.intValue()));
        }
        if (type == Particle.GeyserBase.class) {
            return number(token, 1.0d).map(blocks -> new Particle.GeyserBase(blocks.intValue(), SIZE));
        }
        return Optional.empty();
    }

    private static Location above(Location at) {
        return at.clone().add(0, 1, 0);
    }

    private static Optional<Color> colour(String token) {
        if (token.isEmpty()) {
            return Optional.of(Color.WHITE);
        }
        String hex = token.startsWith("#") ? token.substring(1) : token;
        if (hex.length() != 6) {
            return Optional.empty();
        }
        try {
            return Optional.of(Color.fromRGB(Integer.parseInt(hex, 16)));
        } catch (NumberFormatException notHex) {
            return Optional.empty();
        }
    }

    private static Optional<Object> transition(String token) {
        int arrow = token.indexOf('>');
        String from = arrow < 0 ? token : token.substring(0, arrow);
        String to = arrow < 0 ? token : token.substring(arrow + 1);
        Optional<Color> start = colour(from);
        Optional<Color> end = colour(to);
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Particle.DustTransition(start.get(), end.get(), SIZE));
    }

    private static Optional<Material> material(String token, Predicate<Material> fits) {
        if (token.isEmpty()) {
            return Optional.of(Material.STONE);
        }
        Material named = Material.matchMaterial(token.toUpperCase(Locale.ROOT));
        return named != null && fits.test(named) ? Optional.of(named) : Optional.empty();
    }

    private static Optional<Double> number(String token, double fallback) {
        if (token.isEmpty()) {
            return Optional.of(fallback);
        }
        try {
            double read = Double.parseDouble(token);
            return Double.isFinite(read) ? Optional.of(read) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
