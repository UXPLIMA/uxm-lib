package com.uxplima.uxmlib.hud.nametag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/**
 * The composition rules on their own: parts compose in priority order with the configured separator, the
 * name's single colour goes to the contribution that asked for it last whatever its position, and a layout
 * tie is settled by plugin name so the same contributions produce the same name on every restart.
 */
class ComposedNametagTest {

    private static NametagContribution prefix(String plugin, int priority, String text) {
        return NametagContribution.prefix(plugin, priority, Component.text(text));
    }

    @Test
    void partsComposeInPriorityOrderWithTheSeparatorBetweenThem() {
        ComposedNametag name =
                ComposedNametag.compose(List.of(prefix("tags", 200, "[VIP]"), prefix("clans", 100, "[Wolves]")), " ");

        assertThat(Text.plain(name.prefix())).isEqualTo("[Wolves] [VIP]");
    }

    @Test
    void theSeparatorIsWhateverTheRegistryWasGiven() {
        ComposedNametag name = ComposedNametag.compose(List.of(prefix("a", 1, "x"), prefix("b", 2, "y")), " | ");

        assertThat(Text.plain(name.prefix())).isEqualTo("x | y");
    }

    @Test
    void aSuffixComposesTheSameWayAndIndependently() {
        ComposedNametag name = ComposedNametag.compose(
                List.of(NametagContribution.suffix("level", 100, Component.text("(42)")), prefix("tags", 100, "[VIP]")),
                " ");

        assertThat(Text.plain(name.prefix())).isEqualTo("[VIP]");
        assertThat(Text.plain(name.suffix())).isEqualTo("(42)");
    }

    @Test
    void theLastContributionToAskForTheColourGetsIt() {
        ComposedNametag name = ComposedNametag.compose(
                List.of(
                        NametagContribution.color("tags", 100, NamedTextColor.AQUA),
                        NametagContribution.color("glow", 50, NamedTextColor.RED)),
                " ");

        assertThat(name.color()).isEqualTo(NamedTextColor.RED);
        assertThat(name.colorSources()).containsExactly("tags", "glow");
        assertThat(name.hasColorClash()).isTrue();
    }

    /**
     * The leftmost part is not the one that owns the colour. A tag is worn permanently and sits first; a
     * glow is switched on for right now. If position decided, the player who just turned the glow on would
     * see nothing happen.
     */
    @Test
    void sittingLeftmostDoesNotWinTheColour() {
        ComposedNametag name = ComposedNametag.compose(
                List.of(
                        new NametagContribution("tags", 50, Component.text("[VIP]"), null, NamedTextColor.GOLD),
                        NametagContribution.color("glow", 900, NamedTextColor.RED)),
                " ");

        assertThat(Text.plain(name.prefix())).isEqualTo("[VIP]");
        assertThat(name.color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void aSharedPriorityIsSettledByPluginNameSoRestartsAgree() {
        List<NametagContribution> contributions = List.of(prefix("zeta", 100, "[Z]"), prefix("alpha", 100, "[A]"));

        ComposedNametag first = ComposedNametag.compose(contributions, " ");
        ComposedNametag reversed = ComposedNametag.compose(contributions.reversed(), " ");

        assertThat(Text.plain(first.prefix())).isEqualTo("[A] [Z]");
        assertThat(Text.plain(reversed.prefix())).isEqualTo("[A] [Z]");
    }

    @Test
    void oneColourIsNoClash() {
        ComposedNametag name =
                ComposedNametag.compose(List.of(NametagContribution.color("glow", 100, NamedTextColor.RED)), " ");

        assertThat(name.hasColorClash()).isFalse();
        assertThat(name.colorSources()).containsExactly("glow");
    }

    @Test
    void noContributionsComposeToAnEmptyNameWithNoColour() {
        ComposedNametag name = ComposedNametag.compose(List.of(), " ");

        assertThat(Text.plain(name.prefix())).isEmpty();
        assertThat(Text.plain(name.suffix())).isEmpty();
        assertThat(name.color()).isNull();
        assertThat(name.colorSources()).isEmpty();
    }

    @Test
    void aContributionThatCarriesNothingIsRejected() {
        assertThat(NametagContribution.DEFAULT_PRIORITY).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new NametagContribution("ghost", 100, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix");
    }
}
