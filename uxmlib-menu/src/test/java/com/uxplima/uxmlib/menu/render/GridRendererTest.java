package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.GridSpec;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.GridViewState;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Where the grid's brush actually lands. The record refuses a caller button in the two columns the engine paints its
 * page arrows in, and its own tests pin that refusal; these pin the other half, that the arrows land where the
 * refusal says they will.
 *
 * <p>The slots are written here as absolute numbers rather than as {@code base + GridSpec.NEXT_COLUMN}, deliberately.
 * Read from the constants, this file would restate what the renderer was told and would follow it anywhere, including
 * somewhere wrong. Written out, it is an independent statement of where the buttons go, so moving a constant makes
 * the record and the guard agree with each other and makes this fail. That is what the pair is for.
 */
class GridRendererTest {

    /** A catalogue that hands every key straight back, so nothing here depends on a message file. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text(key);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    // Built on demand rather than held in static fields: an ItemStack needs a server, and a static initialiser runs
    // before the mock one is up.

    private static ItemStack empty() {
        return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    }

    private static ItemStack blocker() {
        return new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    }

    private static ItemStack prev() {
        return new ItemStack(Material.ARROW);
    }

    private static ItemStack next() {
        return new ItemStack(Material.SPECTRAL_ARROW);
    }

    private static ItemStack control() {
        return new ItemStack(Material.BARRIER);
    }

    private GridRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new GridRenderer(new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A six-row menu needs a seventh row it cannot have, so its canvas pages: the case that draws both arrows. */
    private static GridSpec paginating() {
        return new GridSpec(
                Component.text("grid"),
                6,
                Map::of,
                empty(),
                blocker(),
                prev(),
                next(),
                java.util.List.of(new GridSpec.Control(4, control(), player -> {})));
    }

    private Inventory draw(GridSpec spec, GridViewState state, int page) {
        Inventory inv = Bukkit.createInventory(null, GridRenderer.windowRows(spec.menuRows()) * 9);
        renderer.populate(inv, spec, state, viewer, page);
        return inv;
    }

    @Test
    void theNextArrowIsPaintedInTheLastColumnOfTheControlRow() {
        Inventory inv = draw(paginating(), new GridViewState("spec", "handlers"), 0);
        assertThat(inv.getItem(53)).isEqualTo(next());
    }

    @Test
    void thePreviousArrowIsPaintedInTheFirstColumnOfTheControlRow() {
        Inventory inv = draw(paginating(), new GridViewState("spec", "handlers"), 1);
        assertThat(inv.getItem(45)).isEqualTo(prev());
    }

    /**
     * The arrows are drawn on every page, so the control row has no hole where a page is missing; one with no page to
     * reach is not recorded as an arrow, so pressing it turns nothing. Without the second half, a renderer that
     * recorded both arrows on every page would pass the two tests above.
     */
    @Test
    void anArrowWithNoPageToReachIsPaintedButTurnsNothing() {
        GridViewState onFirst = new GridViewState("spec", "handlers");
        Inventory first = draw(paginating(), onFirst, 0);
        assertThat(first.getItem(45)).isEqualTo(prev());
        assertThat(onFirst.isPrev(45)).isFalse();
        assertThat(onFirst.isNext(53)).isTrue();

        GridViewState onLast = new GridViewState("spec", "handlers");
        Inventory last = draw(paginating(), onLast, 1);
        assertThat(last.getItem(53)).isEqualTo(next());
        assertThat(onLast.isNext(53)).isFalse();
        assertThat(onLast.isPrev(45)).isTrue();
    }

    /** A canvas that fits in one page still shows both arrows, and neither turns a page. */
    @Test
    void aCanvasThatDoesNotPaginateShowsArrowsThatTurnNothing() {
        GridSpec spec = new GridSpec(
                Component.text("grid"), 3, Map::of, empty(), blocker(), prev(), next(), java.util.List.of());
        GridViewState state = new GridViewState("spec", "handlers");
        Inventory inv = draw(spec, state, 0);

        assertThat(inv.getItem(27)).isEqualTo(prev());
        assertThat(inv.getItem(35)).isEqualTo(next());
        assertThat(state.isPrev(27)).isFalse();
        assertThat(state.isNext(35)).isFalse();
    }

    /** A caller's control is drawn in its own column of the same row, offset by that row's base slot. */
    @Test
    void aControlIsPaintedInItsOwnColumnOfTheControlRow() {
        Inventory inv = draw(paginating(), new GridViewState("spec", "handlers"), 0);
        assertThat(inv.getItem(49)).isEqualTo(control());
    }

    /**
     * The nav slots the click router reads must be the slots the brush painted. Recording one and painting another
     * would give a viewer an arrow that does nothing, or a blocker that turns a page.
     */
    @Test
    void theRoutingRecordsTheSlotsTheArrowsWerePaintedIn() {
        GridViewState state = new GridViewState("spec", "handlers");
        draw(paginating(), state, 0);
        assertThat(state.isNext(53)).isTrue();
        assertThat(state.isPrev(45)).isFalse();

        GridViewState second = new GridViewState("spec", "handlers");
        draw(paginating(), second, 1);
        assertThat(second.isPrev(45)).isTrue();
        assertThat(second.isNext(53)).isFalse();
    }
}
