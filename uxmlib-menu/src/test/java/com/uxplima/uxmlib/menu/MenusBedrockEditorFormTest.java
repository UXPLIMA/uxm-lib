package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The editor and the entity list on Bedrock.
 *
 * <p>Every other open in this engine redirects a Floodgate viewer to a native form: a menu spec, and a confirm.
 * These two did not. They built a chest and showed it, so an operator on Bedrock opened a plugin's editor as a
 * form, tapped the first button, and landed in a chest. Nothing in the estate could see it: five plugins carry a
 * guard that every window they ship reaches a form, and every one of them passed, because the windows behind the
 * first one are not menu specs at all. They are drawn by the engine, here.
 */
class MenusBedrockEditorFormTest {

    /** A catalogue that hands every key straight back, so a form's button text is readable in an assertion. */
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

    /** Answers for exactly the players a test names, so one engine can serve a Bedrock and a Java viewer at once. */
    private static final class Detector implements BedrockDetector {

        private final Set<UUID> bedrock = new HashSet<>();

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock.contains(player);
        }
    }

    /** Keeps the last simple form and its callback, so a tap can be replayed by hand. */
    private static final class RecordingScreen implements BedrockScreen {

        private final List<String> sent = new ArrayList<>();

        private @Nullable String title;

        private @Nullable List<BedrockButton> buttons;

        private @Nullable IntConsumer onSelect;

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            sent.add("simple");
            this.title = title;
            this.buttons = buttons;
            this.onSelect = onSelect;
        }

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {
            sent.add("modal");
        }

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {
            sent.add("input");
        }

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {
            sent.add("custom");
        }

        private List<String> buttonTexts() {
            return Objects.requireNonNull(buttons, "buttons").stream()
                    .map(BedrockButton::text)
                    .toList();
        }

        private void tap(int index) {
            Objects.requireNonNull(onSelect, "onSelect").accept(index);
        }
    }

    /** One property that remembers being clicked and answers with whatever the subject says now. */
    private static final class Setting implements EditableProperty {

        private final String label;

        private final StringBuilder value;

        private int clicked;

        private Setting(String label, StringBuilder value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public String valueLore(Player viewer) {
            return value.toString();
        }

        @Override
        public Material icon() {
            return Material.PAPER;
        }

        @Override
        public void onClick(PropertyClick click) {
            clicked++;
            value.setLength(0);
            value.append("written");
            click.reopen().run();
        }
    }

    private PlayerMock viewer;

    private Detector detector;

    private RecordingScreen screen;

    private Menus menus;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        detector = new Detector();
        detector.bedrock.add(viewer.getUniqueId());
        screen = new RecordingScreen();
        ConditionRegistry conditions = new ConditionRegistry();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), conditions);
        menus = new Menus(
                renderer,
                new SameThreadScheduler(),
                new ListSourceRegistry(),
                new EditorRenderer(new PlainText(), Theme::defaults),
                new ActionRegistry(),
                conditions,
                null,
                detector,
                screen);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a Bedrock viewer opening an editor gets a form and no chest behind it")
    void anEditorIsAFormOnBedrock() {
        menus.openEditor(viewer, editorOf(new Setting("gui.name", new StringBuilder("Sharpness"))), "subject");

        assertThat(screen.sent).containsExactly("simple");
        assertThat(screen.title)
                .describedAs("the form is titled with the editor's own title, not with the plugin's name")
                .isEqualTo("Editing");
        assertThat(holderOfOpenWindow())
                .describedAs("a chest opened behind the form would sit there holding a holder nobody can reach")
                .isNull();
    }

    @Test
    @DisplayName("every property is a button, labelled with the setting and the value it holds now")
    void everyPropertyIsAButton() {
        menus.openEditor(
                viewer,
                editorOf(
                        new Setting("gui.name", new StringBuilder("Sharpness")),
                        new Setting("gui.max", new StringBuilder("5"))),
                "subject");

        assertThat(screen.buttonTexts()).containsExactly("gui.name\nSharpness", "gui.max\n5", "gui.back");
    }

    @Test
    @DisplayName("tapping a property runs the click the chest runs, and the form comes back with the new value")
    void tappingAPropertyRunsItAndReopens() {
        StringBuilder value = new StringBuilder("Sharpness");
        Setting name = new Setting("gui.name", value);
        menus.openEditor(viewer, editorOf(name), "subject");

        screen.tap(0);

        assertThat(name.clicked).isEqualTo(1);
        assertThat(screen.buttonTexts())
                .describedAs("the reopen re-reads the subject, so the value the operator just wrote is on the form")
                .containsExactly("gui.name\nwritten", "gui.back");
    }

    @Test
    @DisplayName("the back button is the last one, and tapping it runs the editor's own back")
    void backIsAButton() {
        List<String> went = new ArrayList<>();
        EditorSpec spec = EditorSpec.builder()
                .layout(EntityEditorLayout.codeDefault(List.of(11), 22))
                .title((player, subject) -> Component.text("Editing"))
                .valueLore("gui.value")
                .backName("gui.back")
                .properties(subject -> List.of())
                .onBack(player -> went.add("back"))
                .build();

        menus.openEditor(viewer, spec, "subject");
        screen.tap(0);

        assertThat(screen.buttonTexts()).containsExactly("gui.back");
        assertThat(went).containsExactly("back");
    }

    @Test
    @DisplayName("an editor that can delete offers it as a button, after back")
    void deleteIsAButtonWhenTheEditorHasOne() {
        List<String> deleted = new ArrayList<>();
        EditorSpec spec = EditorSpec.builder()
                .layout(EntityEditorLayout.withDelete(List.of(11), 22, 26))
                .title((player, subject) -> Component.text("Editing"))
                .valueLore("gui.value")
                .backName("gui.back")
                .properties(subject -> List.of())
                .onBack(player -> {})
                .onDelete("gui.delete", "gui.delete.confirm", (player, subject) -> deleted.add("gone"))
                .build();

        menus.openEditor(viewer, spec, "subject");

        assertThat(screen.buttonTexts()).containsExactly("gui.back", "gui.delete");
        screen.tap(1);
        assertThat(deleted).containsExactly("gone");
    }

    @Test
    @DisplayName("a Java viewer on the same engine still gets the chest")
    void ajavaViewerStillGetsTheChest() {
        PlayerMock java = MockBukkit.getMock().addPlayer();

        menus.openEditor(java, editorOf(new Setting("gui.name", new StringBuilder("Sharpness"))), "subject");

        assertThat(screen.sent).isEmpty();
        assertThat(java.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    @DisplayName("an entity list is a form too, one button per entity and no paging")
    void anEntityListIsAForm() {
        List<String> chosen = new ArrayList<>();
        EntityListSpec spec = EntityListSpec.builder()
                .title(Component.text("Crates"))
                .rows(6)
                .contentSlots(List.of(10, 11))
                .navigation(48, 50, Material.ARROW)
                .navNames(Component.text("Previous"), Component.text("Next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .entities(() -> List.of("common", "elite", "mythic"))
                .iconRenderer((player, entity) -> ItemBuilder.of(Material.CHEST)
                        .name(Component.text(String.valueOf(entity)))
                        .build())
                .onSelect((player, entity) -> chosen.add(String.valueOf(entity)))
                .build();

        menus.openList(viewer, spec);

        assertThat(screen.buttonTexts())
                .describedAs("a form scrolls, so every entity is on it and the two nav buttons have nothing to do")
                .containsExactly("common", "elite", "mythic");
        assertThat(screen.title).isEqualTo("Crates");
        screen.tap(1);
        assertThat(chosen).containsExactly("elite");
    }

    /** MockBukkit hands back a null top inventory once a window is closed, so every read of one goes through here. */
    private @Nullable Object holderOfOpenWindow() {
        org.bukkit.inventory.Inventory top = viewer.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }

    /** An editor over one subject with the properties a test names. */
    private static EditorSpec editorOf(EditableProperty... properties) {
        List<EditableProperty> list = List.of(properties);
        return EditorSpec.builder()
                .layout(EntityEditorLayout.codeDefault(List.of(11, 12, 13), 22))
                .title((player, subject) -> Component.text("Editing"))
                .valueLore("gui.value")
                .backName("gui.back")
                .properties(subject -> list)
                .onBack(player -> {})
                .build();
    }
}
