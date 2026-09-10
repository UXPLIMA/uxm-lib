package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.render.ConfirmRenderer;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.runtime.MenuListener;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * How many times a delete asks before it deletes.
 *
 * <p>Once. A destructive button is gated, and a gate a player has to pass twice is not twice as safe: it is a
 * player who stops reading the second window because the first one already asked. Both windows say the same
 * sentence, so the second one carries no information at all.
 *
 * <p>The estate shipped two. {@link EntityEditorView} handed the engine a confirm title, which makes the engine
 * gate the delete button, and then gated the delete again itself: the yes of the first window opened a second
 * window. Six plugins draw their editors through this view, so every one of them asked twice, and eight tests in
 * uxmEssentials that clicked yes once and expected the delete were red because of it.
 *
 * <p>The gate stays on the view rather than on the engine, for two reasons. Its no reopens the editor the player
 * came from, where the engine's no runs the caller's back handler and lands them on the list. And the view's gate
 * goes through {@link Menus#confirm}, which draws a native form for a Bedrock viewer: the engine's editor form
 * calls the delete handler straight, so a gate that lived only there would be a Java-only gate.
 */
class EntityEditorViewDeleteTest {

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

    /** A property that does nothing, so the only button under test is the delete one. */
    private record Fixed(String label) implements EditableProperty {

        @Override
        public Material icon() {
            return Material.DIAMOND;
        }

        @Override
        public String valueLore(Player viewer) {
            return "value";
        }

        @Override
        public void onClick(PropertyClick click) {}
    }

    /** What the editor edits. */
    private record Subject(String name) {}

    private static final int DELETE_SLOT = 26;

    private final List<String> deleted = new ArrayList<>();

    private final List<String> backs = new ArrayList<>();

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        deleted.clear();
        backs.clear();
        SameThreadScheduler scheduler = new SameThreadScheduler();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
        menus = new Menus(
                renderer, scheduler, new ListSourceRegistry(), new EditorRenderer(new PlainText(), Theme::defaults));
        listener = new MenuListener(renderer, new ActionRegistry(), new ConditionRegistry(), scheduler, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the delete button asks once, and the yes of that one window deletes")
    void oneConfirmAndTheYesDeletes() {
        open();

        click(DELETE_SLOT);
        assertThat(deleted)
                .describedAs("the button opens the gate, it does not delete")
                .isEmpty();
        assertThat(confirmIsOpen())
                .describedAs("a confirm window is what the button opens")
                .isTrue();

        click(ConfirmRenderer.YES_SLOT);

        assertThat(deleted)
                .describedAs("a second window here is a second identical question, which is the one nobody reads")
                .containsExactly("home");
    }

    @Test
    @DisplayName("the no of that window reopens the editor rather than dropping the player on the list")
    void theNoComesBackToTheEditor() {
        open();

        click(DELETE_SLOT);
        click(ConfirmRenderer.NO_SLOT);

        assertThat(deleted).isEmpty();
        assertThat(backs).describedAs("cancelling a delete is not going back").isEmpty();
        assertThat(viewer.getOpenInventory().getTopInventory().getItem(DELETE_SLOT))
                .describedAs("the editor is drawn again, delete button and all")
                .isNotNull();
    }

    private void open() {
        EntityEditorView<Subject> view = EntityEditorView.<Subject>builder()
                .menus(menus)
                .guiText(new PlainText())
                .layout(EntityEditorLayout.withDelete(List.of(10), 22, DELETE_SLOT))
                .title((who, subject) -> Component.text("editing " + subject.name()))
                .valueLore("value: %value%")
                .backName("back")
                .properties(subject -> List.of(new Fixed("difficulty")))
                .onBack(who -> backs.add(who.getName()))
                .onDelete("delete", "really delete?", (who, subject) -> deleted.add(subject.name()))
                .build();
        view.open(viewer, new Subject("home"));
    }

    private boolean confirmIsOpen() {
        return viewer.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder
                && holder.confirm().isPresent();
    }

    private void click(int rawSlot) {
        InventoryView view = viewer.getOpenInventory();
        listener.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }
}
