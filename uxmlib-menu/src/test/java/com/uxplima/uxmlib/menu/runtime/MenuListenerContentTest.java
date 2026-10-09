package com.uxplima.uxmlib.menu.runtime;

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ContentProviderRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.providers.ContentClick;
import com.uxplima.uxmlib.menu.providers.ContentProvider;
import com.uxplima.uxmlib.menu.providers.OwnRowsClick;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.ContentRegionSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A content region is the one place an item may move inside a menu window, so it is the one place the router can
 * mint or eat one. Everything here is about who is asked, and what the answer is allowed to change.
 *
 * <p>The blanket cancel is the default the whole engine rests on: a test that only asserts "the click was cancelled"
 * would pass on every path, including the ones that were supposed to lift it. So each refusal below is paired with
 * the allowing case that proves the lift was reachable at all.
 */
class MenuListenerContentTest {

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

    /** Answers one fixed verdict and records every click it was asked about. */
    private static final class Recording implements ContentProvider {

        private final boolean verdict;

        private final List<ContentClick> asked = new ArrayList<>();

        Recording(boolean verdict) {
            this.verdict = verdict;
        }

        @Override
        public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            return List.of();
        }

        @Override
        public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            asked.add(click);
            return verdict;
        }
    }

    /**
     * A feature that keeps its items on its own record: it records the clicks handed to it on a read-only region and
     * in the viewer's own rows, takes the own-rows ones or not as told, and allows every movement it is asked about.
     */
    private static final class Keeping implements ContentProvider {

        private final boolean takes;

        private final List<ContentClick> clicked = new ArrayList<>();

        private final List<OwnRowsClick> ownRows = new ArrayList<>();

        Keeping(boolean takes) {
            this.takes = takes;
        }

        @Override
        public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            return List.of();
        }

        @Override
        public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            return true;
        }

        @Override
        public void clicked(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            clicked.add(click);
        }

        @Override
        public boolean ownRowsClicked(MenuContext ctx, ContentRegionSpec region, OwnRowsClick click) {
            ownRows.add(click);
            return takes;
        }
    }

    /** Refuses every movement onto one named slot and allows the rest, so per-slot granularity is observable. */
    private static final class Reserving implements ContentProvider {

        private final int reserved;

        Reserving(int reserved) {
            this.reserved = reserved;
        }

        @Override
        public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            return List.of();
        }

        @Override
        public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            return click.slot() != reserved;
        }
    }

    private static final String SPEC = "rows = 3\ncontent { deposit { slots = [1, 2], editable = true } }";

    private ContentProviderRegistry contents;

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        contents = new ContentProviderRegistry();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()),
                new ConditionRegistry(),
                contents);
        SameThreadScheduler scheduler = new SameThreadScheduler();
        menus = new Menus(renderer, scheduler, new ListSourceRegistry());
        listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                () -> 1_000_000L,
                new PagedListSourceRegistry(),
                null,
                contents);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void open(String hocon) {
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
    }

    private Inventory top() {
        return viewer.getOpenInventory().getTopInventory();
    }

    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action) {
        InventoryView view = viewer.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        listener.onClick(event);
        return event;
    }

    private InventoryClickEvent leftClick(int rawSlot) {
        return click(rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static ItemStack diamond() {
        return new ItemStack(Material.DIAMOND);
    }

    /**
     * Put {@code stack} in the viewer's own inventory and hand back the raw slot the open view maps it to. The
     * mapping from a player's inventory index to a raw view slot is the server's business and is not worth
     * hardcoding: a raw slot that holds nothing makes the router return before it decides anything, and every test
     * here that asserts nothing moved would pass on the strength of that alone. So the slot is found, and not
     * finding one fails loudly rather than quietly weakening four tests.
     */
    private int holdInOwnRows(ItemStack stack) {
        viewer.getInventory().setItem(9, stack);
        InventoryView view = viewer.getOpenInventory();
        for (int raw = view.getTopInventory().getSize(); raw < view.countSlots(); raw++) {
            if (stack.equals(view.getItem(raw))) {
                return raw;
            }
        }
        throw new IllegalStateException("the open view maps no raw slot to the stack just placed");
    }

    // -- who is asked -----------------------------------------------------------------------------------------

    @Test
    void aRegionThatIsNotEditableIsNeverEvenPutToItsProvider() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open("rows = 3\ncontent { deposit { slots = [1, 2] } }");

        assertThat(leftClick(1).isCancelled()).isTrue();
        assertThat(provider.asked).isEmpty();
    }

    @Test
    void anEditableRegionWithNoRegisteredProviderStaysShut() {
        open(SPEC);
        assertThat(leftClick(1).isCancelled()).isTrue();
    }

    @Test
    void aProviderThatAllowsTheMovementLiftsTheCancel() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(leftClick(1).isCancelled()).isFalse();
        assertThat(provider.asked).hasSize(1);
    }

    @Test
    void aProviderThatRefusesTheMovementLeavesItCancelled() {
        Recording provider = new Recording(false);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(leftClick(1).isCancelled()).isTrue();
        assertThat(provider.asked).hasSize(1);
    }

    @Test
    void aSlotOutsideEveryRegionIsNotTheProvidersBusiness() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(leftClick(5).isCancelled()).isTrue();
        assertThat(provider.asked).isEmpty();
    }

    // -- the gestures no provider is allowed to permit ----------------------------------------------------------

    /**
     * A double-click gathers matching stacks from the whole window, and the chrome tiles are not real items, so
     * letting one through would mint them. The provider is not asked, because there is no one slot to ask about.
     */
    @Test
    void aDoubleClickIsRefusedEvenInARegionWhoseProviderAllowsEverything() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(click(1, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR)
                        .isCancelled())
                .isTrue();
        assertThat(provider.asked).isEmpty();
    }

    /** A hotbar swap pulls from a slot outside the region, which is not the movement the provider was asked about. */
    @Test
    void aHotbarSwapIsRefusedEvenInARegionWhoseProviderAllowsEverything() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(click(1, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP).isCancelled())
                .isTrue();
        assertThat(provider.asked).isEmpty();
    }

    @Test
    void anOffHandSwapIsRefusedTheSameWay() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(click(1, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP).isCancelled())
                .isTrue();
        assertThat(provider.asked).isEmpty();
    }

    // -- what the provider is told the viewer is doing ----------------------------------------------------------

    /**
     * The three kinds are decided from the cursor and the slot together, so a provider can allow taking out while
     * refusing putting in. Asserted as one test because each kind alone would pass an implementation that always
     * reported it.
     */
    @Test
    void theKindIsReadFromTheCursorAndTheSlotTogether() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        top().setItem(1, diamond());
        viewer.getOpenInventory().setCursor(null);
        leftClick(1);
        assertThat(provider.asked.get(0).kind()).isEqualTo(ContentClick.Kind.TAKE);

        top().setItem(1, null);
        viewer.getOpenInventory().setCursor(diamond());
        leftClick(1);
        assertThat(provider.asked.get(1).kind()).isEqualTo(ContentClick.Kind.INSERT);

        top().setItem(1, diamond());
        viewer.getOpenInventory().setCursor(diamond());
        leftClick(1);
        assertThat(provider.asked.get(2).kind()).isEqualTo(ContentClick.Kind.SWAP);
    }

    /** A shift-click takes out whatever the cursor holds, so it is a TAKE rather than a SWAP. */
    @Test
    void aShiftClickOnAFilledSlotIsATakeWhateverTheCursorHolds() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        top().setItem(1, diamond());
        viewer.getOpenInventory().setCursor(diamond());

        click(1, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(provider.asked.get(0).kind()).isEqualTo(ContentClick.Kind.TAKE);
    }

    @Test
    void theClickCarriesTheSlotAndItsPositionWithinTheRegion() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        leftClick(2);

        assertThat(provider.asked.get(0).slot()).isEqualTo(2);
        assertThat(provider.asked.get(0).index()).isEqualTo(1);
    }

    // -- the shift-click the engine performs itself -------------------------------------------------------------

    /**
     * Vanilla would scatter a shift-clicked stack across whatever top slots are free, chrome gaps included, so the
     * engine performs the insert itself: into the region's first free slot, with the source cleared and the event
     * left cancelled so vanilla does not also move it.
     */
    @Test
    void aShiftClickFromTheViewersOwnRowsLandsInTheRegionsFirstFreeSlot() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        top().setItem(1, diamond());
        int raw = holdInOwnRows(new ItemStack(Material.EMERALD));

        InventoryClickEvent event = click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(event.isCancelled()).isTrue();
        assertThat(top().getItem(2)).isEqualTo(new ItemStack(Material.EMERALD));
        assertThat(event.getCurrentItem()).isNull();
    }

    @Test
    void aShiftClickTheProviderRefusesMovesNothing() {
        Recording provider = new Recording(false);
        contents.register("deposit", provider);
        open(SPEC);
        click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isNull();
        assertThat(top().getItem(2)).isNull();
    }

    @Test
    void aShiftClickIntoAFullRegionMovesNothing() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        top().setItem(1, diamond());
        top().setItem(2, diamond());
        click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isEqualTo(diamond());
        assertThat(top().getItem(2)).isEqualTo(diamond());
    }

    /**
     * A provider is asked per movement everywhere else in the router, and the shift path is no exception: refusing
     * the region's first free slot is a statement about that slot, not about the region. A provider that reserves its
     * first slot and takes the rest is saying something the contract lets it say, so the stack goes to the next slot
     * that accepts it rather than nowhere.
     */
    @Test
    void aRefusalOnOneSlotSkipsThatSlotRatherThanTheWholeRegion() {
        contents.register("deposit", new Reserving(1));
        open(SPEC);
        int raw = holdInOwnRows(new ItemStack(Material.EMERALD));

        click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isNull();
        assertThat(top().getItem(2)).isEqualTo(new ItemStack(Material.EMERALD));
    }

    /** A plain click in the viewer's own rows is not an insert: only a shift-click asks the engine to move a stack. */
    @Test
    void aPlainClickInTheViewersOwnRowsMovesNothing() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        leftClick(holdInOwnRows(new ItemStack(Material.EMERALD)));

        assertThat(top().getItem(1)).isNull();
        assertThat(provider.asked).isEmpty();
    }

    /**
     * A stack is picked up in the viewer's own rows and put down in the region, so the pick up goes through. A
     * window that refused it left the shift click alone, and a vault page could not take half a stack.
     */
    @Test
    void aPlainClickInTheViewersOwnRowsGoesThroughInAWindowThatHoldsItems() {
        contents.register("deposit", new Recording(true));
        open(SPEC);

        InventoryClickEvent event = leftClick(holdInOwnRows(new ItemStack(Material.EMERALD)));

        assertThat(event.isCancelled()).isFalse();
    }

    /** A double click gathers from the whole window, chrome included, so it stays refused from the viewer's rows too. */
    @Test
    void aDoubleClickInTheViewersOwnRowsStaysRefused() {
        contents.register("deposit", new Recording(true));
        open(SPEC);

        InventoryClickEvent event = click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR);

        assertThat(event.isCancelled()).isTrue();
    }

    /** A window whose regions take nothing keeps the viewer's rows shut, as every menu did before regions existed. */
    @Test
    void aWindowThatTakesNothingKeepsTheViewersRowsShut() {
        open("rows = 3\ncontent { mirror { slots = [1, 2], editable = false } }");

        InventoryClickEvent event = leftClick(holdInOwnRows(new ItemStack(Material.EMERALD)));

        assertThat(event.isCancelled()).isTrue();
    }

    /** An editable region with no provider behind it takes nothing either, so the rows stay shut. */
    @Test
    void aRegionWithNoProviderKeepsTheViewersRowsShut() {
        open(SPEC);

        InventoryClickEvent event = leftClick(holdInOwnRows(new ItemStack(Material.EMERALD)));

        assertThat(event.isCancelled()).isTrue();
    }

    // -- a feature that keeps its items itself -------------------------------------------------------------------

    /** A trade's own offer is painted from its record: a click on it is handed over, and nothing moves in the window. */
    @Test
    void aClickOnAReadOnlyRegionIsHandedToItsProviderAndMovesNothing() {
        Keeping provider = new Keeping(false);
        contents.register("deposit", provider);
        open("rows = 3\ncontent { deposit { slots = [1, 2] } }");
        top().setItem(2, new ItemStack(Material.EMERALD));

        assertThat(leftClick(2).isCancelled()).isTrue();
        assertThat(provider.clicked).singleElement().satisfies(click -> {
            assertThat(click.index()).isEqualTo(1);
            assertThat(click.taken()).contains(new ItemStack(Material.EMERALD));
        });
        assertThat(top().getItem(2)).isEqualTo(new ItemStack(Material.EMERALD));
    }

    /** A gesture that reaches beyond the one slot is not handed over on a read-only region either. */
    @Test
    void aDoubleClickOnAReadOnlyRegionIsNotHandedOver() {
        Keeping provider = new Keeping(false);
        contents.register("deposit", provider);
        open("rows = 3\ncontent { deposit { slots = [1, 2] } }");
        top().setItem(2, new ItemStack(Material.EMERALD));

        click(2, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);

        assertThat(provider.clicked).isEmpty();
    }

    /** A stack clicked in the viewer's own rows is handed over with where it sits, a copy of it and the gesture. */
    @Test
    void aClickOnAStackInTheViewersOwnRowsIsHandedToTheProvider() {
        Keeping provider = new Keeping(true);
        contents.register("deposit", provider);
        open("rows = 3\ncontent { deposit { slots = [1, 2] } }");

        int raw = holdInOwnRows(new ItemStack(Material.EMERALD));
        InventoryClickEvent event = click(raw, ClickType.RIGHT, InventoryAction.PICKUP_HALF);

        assertThat(event.isCancelled()).isTrue();
        assertThat(provider.ownRows).singleElement().satisfies(click -> {
            // The slot the view itself names for the raw slot: MockBukkit numbers it unlike a server, so not a literal.
            assertThat(click.inventorySlot())
                    .isEqualTo(viewer.getOpenInventory().convertSlot(raw));
            assertThat(click.stack()).isEqualTo(new ItemStack(Material.EMERALD));
            assertThat(click.gesture()).isEqualTo(ClickKind.RIGHT);
        });
    }

    /** A provider that takes the click as its own move stops the engine's shift insert: the stack moves once. */
    @Test
    void aProviderThatTakesTheOwnRowsClickStopsTheShiftInsert() {
        contents.register("deposit", new Keeping(true));
        open(SPEC);

        InventoryClickEvent event = click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(event.isCancelled()).isTrue();
        assertThat(top().getItem(1)).isNull();
    }

    /** A provider that leaves the click alone lets it go on, here as the engine's shift insert. */
    @Test
    void aProviderThatLeavesTheOwnRowsClickLetsItGoOn() {
        contents.register("deposit", new Keeping(false));
        open(SPEC);

        click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isEqualTo(new ItemStack(Material.EMERALD));
    }

    /** A double click in the viewer's own rows gathers from the whole window, so it is not handed over. */
    @Test
    void aDoubleClickInTheViewersOwnRowsIsNotHandedOver() {
        Keeping provider = new Keeping(true);
        contents.register("deposit", provider);
        open(SPEC);

        click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR);

        assertThat(provider.ownRows).isEmpty();
    }
}
