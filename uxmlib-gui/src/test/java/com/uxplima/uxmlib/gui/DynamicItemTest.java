package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers per-viewer dynamic and stateful items resolving through a RenderContext at open time. */
class DynamicItemTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void dynamicIconResolvesFromTheViewer() {
        SimpleGui gui = Guis.gui().rows(1).build();
        // The icon's amount encodes the viewer's name length, proving per-viewer resolution.
        gui.set(
                0,
                GuiItem.dynamic(ctx ->
                        new ItemStack(Material.PAPER, ctx.viewer().getName().length())));

        Player player = MockBukkit.getMock().addPlayer("Steve"); // length 5
        gui.open(player);

        ItemStack rendered = gui.getInventory().getItem(0);
        assertThat(rendered).isNotNull();
        assertThat(rendered.getType()).isEqualTo(Material.PAPER);
        assertThat(rendered.getAmount()).isEqualTo(5);
    }

    @Test
    void statefulItemPicksTheFirstMatchingState() {
        SimpleGui gui = Guis.gui().rows(1).build();
        GuiItem.Stateful toggle = GuiItem.stateful()
                .display(ctx -> ctx.viewer().getName().startsWith("A"), new ItemStack(Material.EMERALD))
                .display(ctx -> true, new ItemStack(Material.REDSTONE))
                .build();
        gui.set(0, toggle);

        Player alex = MockBukkit.getMock().addPlayer("Alex");
        gui.open(alex);
        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.EMERALD);

        Player steve = MockBukkit.getMock().addPlayer("Steve");
        gui.open(steve);
        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.REDSTONE);
    }

    @Test
    void statefulWithNoMatchRendersEmpty() {
        SimpleGui gui = Guis.gui().rows(1).build();
        GuiItem.Stateful hidden = GuiItem.stateful()
                .display(ctx -> false, new ItemStack(Material.DIAMOND))
                .build();
        gui.set(0, hidden);

        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player);

        ItemStack rendered = gui.getInventory().getItem(0);
        assertThat(rendered == null || rendered.getType() == Material.AIR).isTrue();
    }

    @Test
    void staticItemsRenderWithoutAViewer() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.set(0, GuiItem.display(new ItemStack(Material.STONE)));

        // No viewer yet: a static item must still render into the backing inventory.
        assertThat(gui.getInventory().getItem(0)).isNotNull();
    }
}
