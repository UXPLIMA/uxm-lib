package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.EditorSpec;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
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
 * A click on a property of an editor the engine opened, routed by a listener that was built without editor support.
 *
 * <p>That pairing is not a hypothetical. Every plugin in the estate builds its listener with the five-argument
 * constructor and its engine with the editor renderer, because the two are built on different lines of the same
 * method and nothing made them agree. uxmCrates shipped that way: opening the crate editor worked, and every click
 * on one of its settings threw {@code an editor listener needs a selector opener} into the console while the window
 * sat there doing nothing. The owner reported it on 2026-09-09 as "editörde hiçbirşey çalışmıyormuş".
 *
 * <p>So the openers travel on the editor rather than on the listener: an editor opened through the engine carries the
 * engine's own, and a listener built with none serves it. The listener's own fields remain the fallback, and the
 * loud failure remains for the one case where neither exists.
 */
class MenuListenerEditorClickTest {

    /** A catalogue that hands every key straight back. */
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

    /** A property that records the click it was given and nothing else. */
    private final class Recording implements EditableProperty {

        @Override
        public String label() {
            return "difficulty";
        }

        @Override
        public Material icon() {
            return Material.DIAMOND;
        }

        @Override
        public String valueLore(Player viewer) {
            return "hard";
        }

        @Override
        public void onClick(PropertyClick click) {
            clicks.add(click);
        }
    }

    private final List<PropertyClick> clicks = new ArrayList<>();

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        clicks.clear();
        SameThreadScheduler scheduler = new SameThreadScheduler();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
        menus = new Menus(
                renderer, scheduler, new ListSourceRegistry(), new EditorRenderer(new PlainText(), Theme::defaults));
        // Exactly how every plugin in the estate builds it: no editor renderer, no selector opener, no confirm opener.
        listener = new MenuListener(renderer, new ActionRegistry(), new ConditionRegistry(), scheduler, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EditorSpec spec() {
        return EditorSpec.builder()
                .layout(EntityEditorLayout.codeDefault(List.of(10), 26))
                .title((who, subject) -> Component.text("Editing"))
                .valueLore("value: %value%")
                .backName("back")
                .properties(subject -> List.of(new Recording()))
                .onBack(who -> {})
                .build();
    }

    private void click(int rawSlot) {
        InventoryView view = viewer.getOpenInventory();
        listener.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    @Test
    void aPropertyClickRunsThroughTheOpenersTheEngineHandedTheEditor() {
        menus.openEditor(viewer, spec(), "a crate");

        click(10);

        assertThat(clicks).hasSize(1);
        assertThat(clicks.get(0).opener()).isNotNull();
        assertThat(clicks.get(0).confirmOpener()).isNotNull();
    }

    /** The reopen hook repaints through the engine's renderer too, so a written value shows without a listener. */
    @Test
    void theReopenHookRepaintsThroughTheEnginesOwnRenderer() {
        menus.openEditor(viewer, spec(), "a crate");
        click(10);

        clicks.get(0).reopen().run();

        Inventory top = viewer.getOpenInventory().getTopInventory();
        assertThat(top.getItem(10)).isNotNull();
        assertThat(top.getItem(10).getType()).isEqualTo(Material.DIAMOND);
    }

    /**
     * An editor that carries no openers and a listener that carries none either is a wiring error, and it still says
     * so rather than swallowing the click. Nothing in production reaches this: only an editor state attached by hand.
     */
    @Test
    void anEditorWiredWithNothingStillFailsLoudly() {
        MenuHolder holder =
                new MenuHolder("editor", new MenuSpecLoader().parse("rows = 3"), MenuContext.of(viewer, "a crate", 0));
        EditorState state = new EditorState(spec(), "a crate");
        holder.attachEditor(state);
        Inventory inv = Bukkit.createInventory(holder, 27);
        holder.attach(inv);
        state.recordProperty(10, new Recording());
        viewer.openInventory(inv);

        assertThatThrownBy(() -> click(10))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("selector opener");
    }
}
