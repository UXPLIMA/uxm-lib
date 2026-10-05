package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Drives {@link GuiListener} directly to cover the per-viewer debounce. Without a Scheduler installed the
 * listener runs the slot action inline, so the second rapid click being dropped is observable.
 *
 * <p>Every listener here reads a clock the test owns. On the wall clock, "immediately again" meant "within
 * 150ms of real time", and a CI JVM paused for longer than that between the two clicks accepted both:
 * uxm-lib build.yml run 7 failed {@code rapidSecondClickFromSameViewerIsDebounced} with 2 instead of 1.
 */
class GuiListenerClickTest {

    private final long[] now = {1_000L};

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static InventoryClickEvent clickFor(PlayerMock player, Gui gui) {
        InventoryView view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        return new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    void rapidSecondClickFromSameViewerIsDebounced() {
        GuiListener listener = new GuiListener(() -> now[0]);
        SimpleGui gui = Guis.gui().rows(1).build();
        int[] runs = {0};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> runs[0]++));
        PlayerMock player = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(player, gui));
        listener.onClick(clickFor(player, gui)); // immediately again, inside the debounce window

        assertThat(runs[0]).isEqualTo(1); // the second click's action is dropped
    }

    @Test
    void debouncedClickIsStillCancelled() {
        GuiListener listener = new GuiListener(() -> now[0]);
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(player, gui));
        InventoryClickEvent second = clickFor(player, gui);
        listener.onClick(second);

        // Even when the action is debounced, the cancel policy still runs so no item leaks.
        assertThat(second.isCancelled()).isTrue();
    }

    @Test
    void quitForgetsTheViewerSoTheirDebounceEntryDoesNotLeak() {
        GuiListener listener = new GuiListener(() -> now[0]);
        SimpleGui gui = Guis.gui().rows(1).build();
        int[] runs = {0};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> runs[0]++));
        PlayerMock player = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(player, gui)); // arms the debounce window for this viewer
        listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(
                player,
                net.kyori.adventure.text.Component.empty(),
                org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        listener.onClick(clickFor(player, gui)); // entry was pruned on quit, so this is accepted, not debounced

        assertThat(runs[0]).isEqualTo(2);
    }

    @Test
    void separateViewersAreNotDebouncedAgainstEachOther() {
        GuiListener listener = new GuiListener(() -> now[0]);
        SimpleGui gui = Guis.gui().rows(1).build();
        int[] runs = {0};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> runs[0]++));
        PlayerMock a = MockBukkit.getMock().addPlayer();
        PlayerMock b = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(a, gui));
        listener.onClick(clickFor(b, gui)); // a different viewer, not debounced by a's click

        assertThat(runs[0]).isEqualTo(2);
    }

    @Test
    void aClickOnceTheWindowHasPassedRunsAgain() {
        GuiListener listener = new GuiListener(() -> now[0]);
        SimpleGui gui = Guis.gui().rows(1).build();
        int[] runs = {0};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> runs[0]++));
        PlayerMock player = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(player, gui));
        now[0] += ClickGuard.DEFAULT_WINDOW.toMillis(); // the window is half-open: exactly its length is outside
        listener.onClick(clickFor(player, gui));

        assertThat(runs[0]).isEqualTo(2);
    }
}
