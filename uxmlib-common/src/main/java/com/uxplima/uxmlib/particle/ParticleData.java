package com.uxplima.uxmlib.particle;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * Validation shared by the {@link ParticleOptions} records. A particle's {@link Particle#getDataType()}
 * declares the payload it expects; checking it at construction turns a "wrong data for this particle" mistake
 * into a clear exception at the call site instead of a {@link ClassCastException} deep inside the server.
 */
final class ParticleData {

    private ParticleData() {}

    /** Fail fast unless {@code particle} declares {@code expected} as its data type. */
    static void requireDataType(Particle particle, Class<?> expected) {
        Class<?> actual = particle.getDataType();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("particle " + particle + " expects data of type "
                    + actual.getSimpleName() + ", not " + expected.getSimpleName());
        }
    }

    /** Dust size is a render scale; a non-positive value would make the particle vanish. */
    static void requirePositiveSize(float size) {
        if (!(size > 0f)) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
    }

    private static final int VIBRATION_TICKS = 20;

    private static final int TRAIL_TICKS = 20;

    private static final float SIZE = 1.0f;

    /**
     * The data {@code particle} needs, read from the word an operator wrote beside its name and placed at {@code at}
     * where it needs a place: a colour {@code #ff0000}, two colours {@code #ff0000>#0000ff} for a transition, a block
     * or an item by name, or a number. A blank word reads as the default: white, stone, or the number the particle
     * reads as ordinary. Empty when the word cannot be read. Every default is a constant and nothing here reads the
     * world, so a caller off the region thread may ask.
     */
    static Optional<Object> read(Particle particle, String written, Location at) {
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
