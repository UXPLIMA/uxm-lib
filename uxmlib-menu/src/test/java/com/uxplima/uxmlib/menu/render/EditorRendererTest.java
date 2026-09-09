package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.EditorSpec;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.SlotFit;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.runtime.EditorState;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * One editor window, painted. The renderer owns the geometry and one presentation decision the canon makes for every
 * plugin: a property's own label is the title line of its lore, and the button's display name is blank, so the
 * tooltip opens on the setting the viewer is looking at rather than on the generic word every property shares.
 *
 * <p>The property list is a function of the subject and is re-read on every draw, so a value changed by a click shows
 * on the next one. That is asserted by drawing twice against a list that has changed in between, which is the only
 * way to tell a re-read from a list captured when the spec was built.
 */
class EditorRendererTest {

    /** A catalogue that hands every key straight back, so a rendered line is readable in an assertion. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            String out = key;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return Component.text(out);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** A property that reports a fixed label, value and icon and records nothing else. */
    private record Fixed(String label, String value, Material icon) implements EditableProperty {

        @Override
        public String valueLore(Player viewer) {
            return value;
        }

        @Override
        public void onClick(PropertyClick click) {}
    }

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final List<EditableProperty> properties = new ArrayList<>();

    private final List<String> pressed = new ArrayList<>();

    private EditorRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new EditorRenderer(new PlainText(), Theme::defaults);
        properties.clear();
        properties.add(new Fixed("difficulty", "hard", Material.DIAMOND));
        properties.add(new Fixed("public", "on", Material.EMERALD));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EditorSpec.Builder editor(EntityEditorLayout layout) {
        return EditorSpec.builder()
                .layout(layout)
                .title((who, subject) -> Component.text("Editing"))
                .valueLore("value: %value%")
                .backName("back")
                .properties(subject -> List.copyOf(properties))
                .onBack(who -> pressed.add("back"));
    }

    private static EntityEditorLayout layout() {
        return EntityEditorLayout.codeDefault(List.of(10, 11, 12), 26);
    }

    private Inventory draw(EditorSpec spec, EditorState state) {
        Inventory inv = Bukkit.createInventory(null, spec.layout().rows() * 9);
        renderer.populate(inv, spec, state, viewer);
        return inv;
    }

    private static Material at(Inventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        return stack == null ? Material.AIR : stack.getType();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> loreOf(Inventory inv, int slot) {
        List<Component> lore = inv.getItem(slot).lore();
        List<String> out = new ArrayList<>();
        for (Component line : lore == null ? List.<Component>of() : lore) {
            out.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return out;
    }

    // -- the geometry -----------------------------------------------------------------------------------------

    @Test
    void eachPropertyLandsOnTheLayoutSlotAtItsOwnPosition() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(at(inv, 10)).isEqualTo(Material.DIAMOND);
        assertThat(at(inv, 11)).isEqualTo(Material.EMERALD);
    }

    @Test
    void aLayoutSlotWithNoPropertyToHoldIsLeftAsFiller() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(at(inv, 12)).isEqualTo(FILLER);
    }

    @Test
    void everySlotTheLayoutDoesNotClaimCarriesTheFiller() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(at(inv, 0)).isEqualTo(FILLER);
        assertThat(at(inv, 13)).isEqualTo(FILLER);
    }

    /**
     * A subject with more properties than the layout has slots draws as many as fit and drops the rest, which is all
     * it can do: a slot that does not exist cannot be painted. What it must not do is drop them in silence, because
     * the operator's layout is always the shorter of the two lists and a miss reads to them as their edit doing
     * nothing. The layout here is this test's own, so the once-per-layout report is this test's to observe.
     */
    @Test
    void propertiesPastTheLayoutsLastSlotAreDrawnNoFurtherButAreReported() {
        List<String> warnings = new ArrayList<>();
        Logger log = Logger.getLogger(SlotFit.class.getName());
        Handler capture = new Handler() {

            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        log.addHandler(capture);
        try {
            properties.add(new Fixed("third", "3", Material.GOLD_INGOT));
            properties.add(new Fixed("fourth", "4", Material.IRON_INGOT));
            EditorState state = new EditorState("spec", "subject");

            Inventory inv = draw(
                    editor(EntityEditorLayout.codeDefault(List.of(1, 2, 3), 26)).build(), state);

            assertThat(at(inv, 3)).isEqualTo(Material.GOLD_INGOT);
            assertThat(state.propertyAt(3)).isPresent();
            assertThat(inv.all(Material.IRON_INGOT)).isEmpty();
            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0)).contains("editor properties");
        } finally {
            log.removeHandler(capture);
        }
    }

    // -- the one presentation decision the renderer makes --------------------------------------------------------

    /**
     * The canon keeps a tile's title on the first lore line and leaves the display name blank. Putting the label in
     * the name would move it outside the tooltip body and leave the lore opening on the generic word every property
     * shares.
     *
     * <p>The assertion is about what the name does not carry rather than its exact text, because this runtime
     * decorates a display name with brackets of its own ({@code Tiles.blankName()} comes back as {@code [ ]}). Those
     * brackets are the mock's and not the renderer's, so pinning them would pin the test runtime.
     */
    @Test
    void thePropertyLabelIsTheTitleOfItsLoreRatherThanItsDisplayName() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        String name = plain(inv.getItem(10).displayName());

        assertThat(name).as("the label does not go in the display name").doesNotContain("difficulty");
        assertThat(name.replaceAll("[\\[\\]\\s]", ""))
                .as("and the name carries no word of its own")
                .isEmpty();
        assertThat(loreOf(inv, 10)).first().asString().contains("difficulty");
    }

    @Test
    void thePropertysCurrentValueGoesThroughTheSpecsValueLoreLine() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(loreOf(inv, 10)).anyMatch(line -> line.contains("value: hard"));
    }

    // -- what a click can reach ---------------------------------------------------------------------------------

    @Test
    void theRoutingRemembersWhichPropertyWasDrawnInWhichSlot() {
        EditorState state = new EditorState("spec", "subject");
        draw(editor(layout()).build(), state);

        assertThat(state.propertyAt(10))
                .get()
                .extracting(EditableProperty::label)
                .isEqualTo("difficulty");
        assertThat(state.propertyAt(11))
                .get()
                .extracting(EditableProperty::label)
                .isEqualTo("public");
        assertThat(state.propertyAt(12)).isEmpty();
    }

    /**
     * A property tile is a tile.
     *
     * <p>Every editor in the estate drew a title and one bare line: no {@code ✎} description, no {@code ≡}
     * header over the value, no {@code →} click line, and the value hard against the left edge with no
     * padding. UI-STYLE 7.2 asks for six filled lines and says a tile with one line of lore looks unfinished
     * next to a competitor. The owner reported it against uxmCrates on 2026-09-08; it was never that plugin's
     * to fix, because this class draws every editor of every plugin.
     */
    @Test
    void aSpecThatNamesItsBlocksDrawsAProperTile() {
        EditorState state = new EditorState("spec", "subject");
        Inventory inv = draw(tiled(), state);

        List<String> lore = loreOf(inv, 10);
        assertThat(lore).anyMatch(line -> line.contains("About"));
        assertThat(lore).anyMatch(line -> line.contains("What this setting decides."));
        assertThat(lore).anyMatch(line -> line.contains("Details"));
        assertThat(lore).anyMatch(line -> line.contains("value: hard"));
        assertThat(lore).anyMatch(line -> line.contains("Click to change it"));
        assertThat(lore)
                .describedAs("UI-STYLE 7.2: a tile that says something carries at least six filled lines")
                .hasSizeGreaterThanOrEqualTo(6);
    }

    /** The tiled editor every shape assertion below is made against: one property, all five blocks named. */
    private EditorSpec tiled() {
        return editor(layout())
                .blocks("Crate", "About", "What this setting decides.", "Details", "Click to change it")
                .build();
    }

    /**
     * The tile closes on exactly one blank line.
     *
     * <p>It closed on two until 2026-09-09, because the lore went to {@link
     * com.uxplima.uxmlib.gui.style.Tiles#titled(Theme, Component, List)} as a list holding one multi-line
     * component. That overload asks whether the last <em>entry</em> is blank, a whole tile is not, so it added a
     * second closing line and every property button in the estate sat a line taller than every other tile. The
     * owner read it off a uxmCrates screenshot: "en alttan 2 boşluk var".
     */
    @Test
    void theTileClosesOnOneBlankLineAndNotOnTwo() {
        List<String> lore = loreOf(draw(tiled(), new EditorState("spec", "subject")), 10);

        assertThat(lore.get(lore.size() - 1)).isEqualTo(" ");
        assertThat(lore.get(lore.size() - 2))
                .describedAs("a tile closes on one blank line, so the line above it says something")
                .isNotBlank();
    }

    /**
     * The breadcrumb sits under the title and the blank line comes after it.
     *
     * <p>UI-STYLE 7.2 fixes the order: title, breadcrumb, blank, {@code ✎} block. A property tile carried no
     * breadcrumb, so its description header sat hard against its title with no air between them, which is what
     * the owner reported on 2026-09-09: "name desc arası boşluk yok".
     */
    @Test
    void theBreadcrumbSitsUnderTheTitleAndTheBlankLineFollowsIt() {
        List<String> lore = loreOf(draw(tiled(), new EditorState("spec", "subject")), 10);

        assertThat(lore.get(0)).contains("difficulty");
        assertThat(lore.get(1)).contains("Crate");
        assertThat(lore.get(2)).isEqualTo(" ");
        assertThat(lore.get(3)).contains("About");
    }

    /**
     * A value that arrives already painted is drawn as it is, and never as the characters of its own tags.
     *
     * <p>A value goes through the catalogue and the catalogue inserts it <em>as text</em>, which is right and
     * has to stay: a text property's value is whatever an operator typed, and one who names a crate {@code
     * <red>} must read those characters back. A toggle's state is not that. It is a coloured word by
     * UI-STYLE 9, and a toggle that answered {@code "<good>On"} had the tag printed on the button. The owner
     * read {@code ANNOUNCED TO THE SERVER <#9AA5BE>ᴏꜰꜰ} off a uxmCrates screen on 2026-09-09.
     */
    @Test
    void aPaintedValueIsDrawnAndNeverPrintedAsItsOwnTags() {
        properties.clear();
        properties.add(new Painted());

        List<String> lore = loreOf(draw(tiled(), new EditorState("spec", "subject")), 10);

        assertThat(lore).anyMatch(line -> line.contains("on"));
        assertThat(lore)
                .describedAs("a painted value never reaches the button as the letters of its own tags")
                .noneMatch(line -> line.contains("<"));
    }

    /** A property that names its own click line gets it, and the editor's is left for the rest. */
    @Test
    void aPropertyThatNamesItsOwnClickLineGetsIt() {
        properties.clear();
        properties.add(new Painted());

        List<String> lore = loreOf(draw(tiled(), new EditorState("spec", "subject")), 10);

        assertThat(lore).anyMatch(line -> line.contains("Click to turn it on and off"));
        assertThat(lore)
                .describedAs("the editor's own click line is not drawn beside the property's")
                .noneMatch(line -> line.contains("Click to change it"));
    }

    /** A property whose value is already a component and which names a click line of its own. */
    private record Painted() implements EditableProperty {

        @Override
        public String label() {
            return "announced";
        }

        @Override
        public Material icon() {
            return Material.BELL;
        }

        /**
         * The plain-text answer a caller with a component in hand is forced into: the component written back
         * out as MiniMessage. That is what uxmCrates handed over, and it is what put the tag on the button.
         */
        @Override
        public String valueLore(Player viewer) {
            return "<green>on";
        }

        @Override
        public java.util.Optional<Component> drawnValue(Player viewer) {
            return java.util.Optional.of(
                    Component.text("on").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }

        @Override
        public String action() {
            return "Click to turn it on and off";
        }

        @Override
        public void onClick(PropertyClick click) {}
    }

    /**
     * A spec that names none of the four keys draws what it always drew, so no consumer's screens change
     * shape on the day the library grows the option.
     */
    @Test
    void aSpecThatNamesNoBlocksIsUnchanged() {
        EditorState state = new EditorState("spec", "subject");
        Inventory inv = draw(editor(layout()).build(), state);

        List<String> lore = loreOf(inv, 10);
        assertThat(lore).anyMatch(line -> line.contains("value: hard"));
        assertThat(lore).noneMatch(line -> line.contains("Details"));
    }

    @Test
    void theBackButtonIsPaintedAtItsSlotAndRoutedToTheSpecsHandler() {
        EditorState state = new EditorState("spec", "subject");
        Inventory inv = draw(editor(layout()).build(), state);

        assertThat(at(inv, 26)).isEqualTo(Material.ARROW);
        state.buttonAt(26).orElseThrow().run();
        assertThat(pressed).containsExactly("back");
    }

    @Test
    void anEditorWithNoDeleteHandlerPaintsNoDeleteButton() {
        EditorState state = new EditorState("spec", "subject");
        Inventory inv = draw(
                editor(EntityEditorLayout.withDelete(List.of(10, 11), 26, 25)).build(), state);

        assertThat(at(inv, 25)).isEqualTo(FILLER);
        assertThat(state.buttonAt(25)).isEmpty();
    }

    @Test
    void aDeclaredDeleteButtonIsPaintedAndRoutedWithTheSubjectItWouldDelete() {
        List<Object> deleted = new ArrayList<>();
        EditorState state = new EditorState("spec", "a-warp");
        Inventory inv = draw(
                editor(EntityEditorLayout.withDelete(List.of(10, 11), 26, 25))
                        .onDelete("delete", "really?", (who, subject) -> deleted.add(subject))
                        .build(),
                state);

        assertThat(at(inv, 25)).isEqualTo(Material.BARRIER);
        state.buttonAt(25).orElseThrow().run();
        assertThat(deleted).containsExactly("a-warp");
    }

    /**
     * The delete button asks first.
     *
     * <p>A spec has always named a confirm title beside its delete handler and the button ran the handler straight
     * through, so a title an author wrote was carried and never drawn: one misclick took the subject away with
     * nothing in between. The gate needs the engine's confirm opener, which an editor opened through the engine
     * now carries.
     */
    @Test
    void aDeleteButtonAsksThroughTheConfirmTheSpecNames() {
        List<Object> deleted = new ArrayList<>();
        List<String> asked = new ArrayList<>();
        EditorState state = new EditorState(
                "spec",
                "a-warp",
                new EditorState.Clicks(
                        renderer,
                        (who, title, rows, filler, buttons) -> {},
                        (who, title, yes, no) -> asked.add(plain(title))));
        draw(
                editor(EntityEditorLayout.withDelete(List.of(10, 11), 26, 25))
                        .onDelete("delete", "really?", (who, subject) -> deleted.add(subject))
                        .build(),
                state);

        state.buttonAt(25).orElseThrow().run();

        assertThat(asked).containsExactly("really?");
        assertThat(deleted)
                .describedAs("nothing goes until the viewer says yes")
                .isEmpty();
    }

    // -- the list is a function of the subject, not a snapshot ---------------------------------------------------

    /**
     * A click changes the subject and the window is repainted, so the property list must be asked again rather than
     * captured when the spec was built. Drawing twice across a change is the only way to tell those apart.
     */
    @Test
    void thePropertyListIsReReadOnEveryDrawRatherThanCapturedOnce() {
        EditorSpec spec = editor(layout()).build();
        draw(spec, new EditorState("spec", "subject"));

        properties.set(0, new Fixed("difficulty", "easy", Material.DIAMOND));
        Inventory second = draw(spec, new EditorState("spec", "subject"));

        assertThat(loreOf(second, 10)).anyMatch(line -> line.contains("value: easy"));
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert each requireNonNull guard fires
    void theRendererRefusesANullWindowSpecStateOrViewer() {
        EditorSpec spec = editor(layout()).build();
        Inventory inv = Bukkit.createInventory(null, 27);
        EditorState state = new EditorState("spec", "subject");

        assertThatNullPointerException().isThrownBy(() -> renderer.populate(null, spec, state, viewer));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, null, state, viewer));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, spec, null, viewer));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, spec, state, null));
    }
}
