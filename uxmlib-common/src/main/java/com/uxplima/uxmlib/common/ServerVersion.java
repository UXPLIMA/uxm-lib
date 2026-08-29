package com.uxplima.uxmlib.common;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;

/**
 * The running Minecraft version as an ordered {@code (major, minor, patch)} key, with an
 * {@link #isAtLeast(int, int, int)} gate for the rare sub-patch where an API symbol only appeared mid-line.
 * We are 1.21+ only, so this carries none of the old multi-version baggage: just a tiny compare.
 *
 * <p>Minecraft dropped the {@code 1.x.y} scheme after 1.21.11 and now releases as {@code 26.1}, {@code 26.2},
 * … The year lands in {@code major} and nothing else changes: 26 sorts above 1, so a version from the new
 * line still compares greater than every version from the old one, and existing gates keep their meaning.
 *
 * <p>The live value is read once from {@link Bukkit#getMinecraftVersion()} and cached; {@link #isFolia()}
 * is a separate, reflective probe for the Folia marker class. Neither holds mutable state callers can change.
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    // "1.21", "1.21.4" or "26.2"; any trailing qualifier ("-R0.1-SNAPSHOT", "-pre2") is ignored.
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    // Folia ships this server class; plain Paper does not. The matching event lives in paper-api, the
    // bare RegionizedServer does not, which makes it a reliable runtime discriminator.
    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final int major;
    private final int minor;
    private final int patch;

    private ServerVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /** A version key from explicit components; all three must be non-negative. */
    public static ServerVersion of(int major, int minor, int patch) {
        nonNegative(major, "major");
        nonNegative(minor, "minor");
        nonNegative(patch, "patch");
        return new ServerVersion(major, minor, patch);
    }

    /**
     * Parse a version string such as {@code "1.21"}, {@code "1.21.4"}, {@code "1.21.11-R0.1-SNAPSHOT"} or the
     * year-based {@code "26.2"}. A missing patch counts as {@code 0}; any trailing qualifier is dropped.
     *
     * @throws IllegalArgumentException if {@code text} does not start with a {@code major.minor} pair
     */
    public static ServerVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = VERSION.matcher(text.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("not a Minecraft version: '" + text + "'");
        }
        String patch = matcher.group(3);
        return of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                patch == null ? 0 : Integer.parseInt(patch));
    }

    /** The running server's version, parsed once from {@link Bukkit#getMinecraftVersion()} and cached. */
    public static ServerVersion current() {
        return Holder.CURRENT;
    }

    /** Whether the server is running Folia, probed by the presence of its regionised-server class. */
    public static boolean isFolia() {
        return Holder.FOLIA;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    /** Whether this version is the same as or newer than {@code major.minor.patch}. */
    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(of(major, minor, patch)) >= 0;
    }

    /** Convenience overload for {@code isAtLeast(major, minor, 0)}. */
    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    @Override
    public int compareTo(ServerVersion other) {
        Objects.requireNonNull(other, "other");
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ServerVersion other && major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static void nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, was " + value);
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName(FOLIA_MARKER);
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }

    // Lazy holder so the Bukkit/Folia probes run on first use (after the server exists), not at class load.
    private static final class Holder {
        private static final ServerVersion CURRENT = parse(Bukkit.getMinecraftVersion());
        private static final boolean FOLIA = detectFolia();

        private Holder() {}
    }
}
