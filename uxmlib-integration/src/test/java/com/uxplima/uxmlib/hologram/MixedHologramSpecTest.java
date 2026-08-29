package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** Pure tests of mixed-line specs and the auto line-stacking geometry: no world, no entities. */
class MixedHologramSpecTest {

    @Test
    void carriesTextItemAndBlockLinesInOrder() {
        // Item/block stacks are mocked: building a real ItemStack or BlockData needs a running server, and
        // the spec only stores them (it never inspects them), so a mock keeps this test pure.
        MixedHologramSpec spec = MixedHologramSpec.builder()
                .text(Component.text("title"))
                .line(new HologramLine.ItemLine(mock(org.bukkit.inventory.ItemStack.class)))
                .line(new HologramLine.BlockLine(mock(org.bukkit.block.data.BlockData.class)))
                .build();

        assertThat(spec.lines()).hasSize(3);
        assertThat(spec.lines().get(0)).isInstanceOf(HologramLine.TextLine.class);
        assertThat(spec.lines().get(1)).isInstanceOf(HologramLine.ItemLine.class);
        assertThat(spec.lines().get(2)).isInstanceOf(HologramLine.BlockLine.class);
    }

    @Test
    void requiresAtLeastOneLine() {
        assertThatThrownBy(() -> MixedHologramSpec.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stacksLinesTopDownAtTheDefaultSpacing() {
        // First line sits at the top; each subsequent line drops by the line's own gap. Offsets are the
        // vertical position of each line relative to the spec's anchor (the first line's location).
        MixedHologramSpec spec = MixedHologramSpec.builder()
                .text(Component.text("a"))
                .text(Component.text("b"))
                .text(Component.text("c"))
                .build();

        List<Double> offsets = spec.stackOffsets();
        assertThat(offsets).hasSize(3);
        assertThat(offsets.get(0)).isEqualTo(0.0);
        assertThat(offsets.get(1)).isLessThan(offsets.get(0)); // each lower line is below the one above
        assertThat(offsets.get(2)).isLessThan(offsets.get(1));
    }

    @Test
    void honoursAcustomLineGap() {
        MixedHologramSpec tight = MixedHologramSpec.builder()
                .lineGap(0.1)
                .text(Component.text("a"))
                .text(Component.text("b"))
                .build();
        MixedHologramSpec loose = MixedHologramSpec.builder()
                .lineGap(0.5)
                .text(Component.text("a"))
                .text(Component.text("b"))
                .build();

        assertThat(tight.stackOffsets().get(1))
                .isGreaterThan(loose.stackOffsets().get(1));
    }

    @Test
    void rejectsANonPositiveLineGap() {
        assertThatThrownBy(() -> MixedHologramSpec.builder().lineGap(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullTextLine() {
        assertThatThrownBy(() -> MixedHologramSpec.builder().text(null)).isInstanceOf(NullPointerException.class);
    }
}
