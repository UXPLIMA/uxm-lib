package com.uxplima.uxmlib.hud.nametag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.jspecify.annotations.Nullable;

/**
 * What every contribution adds up to: the one prefix, one suffix and one colour a player's name can carry.
 *
 * <p>Prefixes and suffixes compose: each contribution's part is appended in priority order, joined by the
 * separator, because a name has room for all of them. A colour does not: the name is drawn in a single
 * colour, so exactly one contribution can own it.
 *
 * <p>Position does not decide that owner. Priority is about layout, and the two questions have different
 * answers: a rank plugin's prefix is worn permanently and belongs leftmost, while a glow a player just
 * switched on belongs to right now. Letting the leftmost part take the colour means the player who turned
 * the glow on sees nothing happen, which reads as a broken feature rather than a precedence rule. So the
 * colour goes to the <em>last</em> plugin that asked for one, and every plugin that asked is recorded in
 * {@link #colorSources} so a registry can say out loud who wanted the name and who has it. A clash nobody
 * reports is what made the original collision so hard to find.
 *
 * <p>Layout ties are broken by the contributing plugin's name, so two plugins that share a priority compose
 * in the same order on every restart rather than in whatever order a map happened to iterate.
 *
 * <p>Pure: no server, no scoreboard. {@link NametagSink} is what turns one of these into a name a player
 * actually wears.
 *
 * @param prefix everything before the player's name, empty when nobody contributed one
 * @param suffix everything after it, empty when nobody contributed one
 * @param color the owning contribution's colour, or {@code null} when no contribution supplied one
 * @param colorSources every plugin that supplied a colour, oldest contribution first; the last one owns it
 */
public record ComposedNametag(
        Component prefix, Component suffix, @Nullable NamedTextColor color, List<String> colorSources) {

    /** Contributions compose by position, then by plugin name so the result survives a restart unchanged. */
    private static final Comparator<NametagContribution> ORDER =
            Comparator.comparingInt(NametagContribution::priority).thenComparing(NametagContribution::plugin);

    public ComposedNametag {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(suffix, "suffix");
        colorSources = List.copyOf(Objects.requireNonNull(colorSources, "colorSources"));
    }

    /**
     * Compose {@code arrivals}, joining the parts of each half with {@code separator}.
     *
     * @param arrivals the contributions in the order they were made, oldest first, which is what settles
     *     the colour; the layout is sorted by priority here and does not depend on it
     */
    public static ComposedNametag compose(List<NametagContribution> arrivals, String separator) {
        Objects.requireNonNull(arrivals, "arrivals");
        Objects.requireNonNull(separator, "separator");
        List<NametagContribution> ordered = new ArrayList<>(arrivals);
        ordered.sort(ORDER);
        List<String> sources = new ArrayList<>();
        NamedTextColor color = null;
        for (NametagContribution contribution : arrivals) {
            NamedTextColor supplied = contribution.color();
            if (supplied != null) {
                sources.add(contribution.plugin());
                color = supplied;
            }
        }
        return new ComposedNametag(
                join(ordered, NametagContribution::prefix, separator),
                join(ordered, NametagContribution::suffix, separator),
                color,
                sources);
    }

    /** Whether more than one contribution wanted the name's colour, so only one of them could have it. */
    public boolean hasColorClash() {
        return colorSources.size() > 1;
    }

    private static Component join(
            List<NametagContribution> ordered,
            Function<NametagContribution, @Nullable Component> part,
            String separator) {
        Component joined = Component.empty();
        boolean first = true;
        for (NametagContribution contribution : ordered) {
            Component piece = part.apply(contribution);
            if (piece == null) {
                continue;
            }
            joined = first ? piece : joined.append(Component.text(separator)).append(piece);
            first = false;
        }
        return joined;
    }
}
