package com.uxplima.uxmlib.hologram.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pure tests of the switchable selection: states are tried in order and the first whose predicate passes for
 * a viewer wins, mirroring the GUI {@code Stateful} pattern. When nothing matches the selection is empty. No
 * server is needed: the viewer context is a plain UUID plus a stat lookup.
 */
class SwitchSelectionTest {

    private static final UUID VIEWER = new UUID(0, 7);

    private static ViewerContext ctx(double rank) {
        return new ViewerContext(VIEWER, stat -> rank);
    }

    @Test
    void firstMatchingStateWins() {
        SwitchSelection<String> selection = new SwitchSelection<>(List.of(
                new SwitchSelection.State<>(c -> c.stat("rank") >= 100, "admin"),
                new SwitchSelection.State<>(c -> c.stat("rank") >= 10, "vip"),
                new SwitchSelection.State<>(c -> true, "default")));

        assertThat(selection.select(ctx(50))).contains("vip"); // skips admin, matches vip before default
    }

    @Test
    void fallsThroughToTheCatchAllState() {
        SwitchSelection<String> selection = new SwitchSelection<>(List.of(
                new SwitchSelection.State<>(c -> c.stat("rank") >= 100, "admin"),
                new SwitchSelection.State<>(c -> true, "default")));

        assertThat(selection.select(ctx(1))).contains("default");
    }

    @Test
    void emptyWhenNoStateMatches() {
        SwitchSelection<String> selection =
                new SwitchSelection<>(List.of(new SwitchSelection.State<>(c -> c.stat("rank") >= 100, "admin")));

        assertThat(selection.select(ctx(1))).isEmpty();
    }

    @Test
    void earlierStateWinsWhenSeveralMatch() {
        SwitchSelection<String> selection = new SwitchSelection<>(List.of(
                new SwitchSelection.State<>(c -> true, "first"), new SwitchSelection.State<>(c -> true, "second")));

        assertThat(selection.select(ctx(0))).contains("first");
    }

    @Test
    void selectionIsPerViewer() {
        SwitchSelection<String> selection = new SwitchSelection<>(List.of(
                new SwitchSelection.State<>(c -> c.player().getLeastSignificantBits() == 1, "one"),
                new SwitchSelection.State<>(c -> true, "other")));

        Optional<String> forOne = selection.select(new ViewerContext(new UUID(0, 1), stat -> 0));
        Optional<String> forTwo = selection.select(new ViewerContext(new UUID(0, 2), stat -> 0));
        assertThat(forOne).contains("one");
        assertThat(forTwo).contains("other");
    }

    @Test
    void rejectsAnEmptyStateList() {
        assertThatThrownBy(() -> new SwitchSelection<>(List.<SwitchSelection.State<String>>of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
