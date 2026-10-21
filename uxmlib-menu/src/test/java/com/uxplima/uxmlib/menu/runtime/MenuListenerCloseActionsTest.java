package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A menu file may say what happens when its window closes, and an operator who wrote {@code close-actions} expects
 * them to run. They run when the window closes for good, and not when another window takes its place or the viewer
 * leaves the server.
 */
class MenuListenerCloseActionsTest {

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

    private final List<String> fired = new ArrayList<>();

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        ActionRegistry actions = new ActionRegistry();
        actions.register("bye", ctx -> fired.add("bye"));
        actions.register("sound", ctx -> fired.add("sound"));
        SameThreadScheduler scheduler = new SameThreadScheduler();
        menus = new Menus(renderer(), scheduler, new ListSourceRegistry());
        listener = MenuListener.builder(renderer(), actions, new ConditionRegistry(), scheduler, plugin)
                .build();
        menus.registerSpec("menu", new MenuSpecLoader().parse("rows = 3\nclose-actions = [ \"bye\", \"sound\" ]"));
        menus.open(viewer, "menu", null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private void close(InventoryCloseEvent.Reason reason) {
        listener.onClose(new InventoryCloseEvent(viewer.getOpenInventory(), reason));
    }

    @Test
    void closingTheWindowRunsItsCloseActionsInOrder() {
        close(InventoryCloseEvent.Reason.PLAYER);

        assertThat(fired).containsExactly("bye", "sound");
    }

    @Test
    void aWindowAPluginClosesRunsThemToo() {
        close(InventoryCloseEvent.Reason.PLUGIN);

        assertThat(fired).containsExactly("bye", "sound");
    }

    @Test
    void anotherWindowTakingItsPlaceRunsNone() {
        close(InventoryCloseEvent.Reason.OPEN_NEW);

        assertThat(fired).isEmpty();
    }

    @Test
    void aViewerWhoLeftTheServerRunsNone() {
        close(InventoryCloseEvent.Reason.DISCONNECT);

        assertThat(fired).isEmpty();
    }
}
