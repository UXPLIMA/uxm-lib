package com.uxplima.uxmlib.hologram.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/** Pure tests of the leaderboard renderer: no server, no entity. */
class LeaderboardRendererTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static String name(UUID id) {
        if (id.equals(A)) {
            return "Alice";
        }
        if (id.equals(B)) {
            return "Bob";
        }
        return "Carol";
    }

    private static Map<UUID, Double> scores() {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        scores.put(A, 10.0);
        scores.put(B, 30.0);
        scores.put(C, 20.0);
        return scores;
    }

    @Test
    void sortsDescendingAndSubstitutesTokens() {
        LeaderboardRenderer renderer = new LeaderboardRenderer(
                LeaderboardOptions.topN(3).format("{place}. {name} {score}"), LeaderboardRendererTest::name);

        List<Component> lines = renderer.render(scores());

        assertThat(Text.plain(lines.get(0))).isEqualTo("1. Bob 30"); // highest first
        assertThat(Text.plain(lines.get(1))).isEqualTo("2. Carol 20");
        assertThat(Text.plain(lines.get(2))).isEqualTo("3. Alice 10");
    }

    @Test
    void padsEmptyPlacesAndAddsHeader() {
        LeaderboardRenderer renderer = new LeaderboardRenderer(
                LeaderboardOptions.topN(4).format("{name}").emptyLine("---").header(Component.text("TOP")),
                LeaderboardRendererTest::name);

        List<Component> lines = renderer.render(scores());

        assertThat(Text.plain(lines.get(0))).isEqualTo("TOP"); // header
        assertThat(Text.plain(lines.get(4))).isEqualTo("---"); // 4th place has no entry
    }

    @Test
    void ascendingReversesOrder() {
        LeaderboardRenderer renderer = new LeaderboardRenderer(
                LeaderboardOptions.topN(1).descending(false).format("{name}"), LeaderboardRendererTest::name);

        List<Component> lines = renderer.render(scores());
        assertThat(Text.plain(lines.get(0))).isEqualTo("Alice"); // lowest score first
    }

    @Test
    void topFormatsApplyToLeadingPlaces() {
        LeaderboardRenderer renderer = new LeaderboardRenderer(
                LeaderboardOptions.topN(2).format("{name}").topFormats(List.of("#1 {name}")),
                LeaderboardRendererTest::name);

        List<Component> lines = renderer.render(scores());
        assertThat(Text.plain(lines.get(0))).isEqualTo("#1 Bob"); // top format
        assertThat(Text.plain(lines.get(1))).isEqualTo("Carol"); // default format
    }
}
