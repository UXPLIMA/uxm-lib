package com.uxplima.uxmlib.gui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.Gui;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.RenderContext;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** Covers drawing the menu a file describes: the tiles, the conditions, the clicks and the computed list. */
class MenuDrawTest {

    private MenuActions actions;
    private MenuConditions conditions;
    private MenuLists lists;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        actions = new MenuActions();
        conditions = new MenuConditions();
        lists = new MenuLists();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static CommentedConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new java.io.StringReader(hocon)))
                .build()
                .load();
    }

    private MenuDraw draw() {
        return draw(MenuDraw.asWritten());
    }

    private MenuDraw draw(MenuDraw.Words words) {
        return new MenuDraw(actions, conditions, lists, words, (viewer, menu) -> {});
    }

    private static String plain(@Nullable Component text) {
        return text == null ? "" : PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static String nameOf(Gui gui, int slot, PlayerMock viewer) {
        GuiItem item = java.util.Objects.requireNonNull(gui.getItem(slot));
        ItemStack icon = item.icon(new RenderContext(viewer, gui, slot));
        return plain(icon.getItemMeta() == null ? null : icon.getItemMeta().displayName());
    }

    private static void click(Gui gui, int slot, PlayerMock viewer, ClickType how) {
        GuiItem item = java.util.Objects.requireNonNull(gui.getItem(slot));
        InventoryView view = java.util.Objects.requireNonNull(viewer.openInventory(gui.getInventory()));
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, how, InventoryAction.PICKUP_ALL);
        item.action(new RenderContext(viewer, gui, slot)).accept(event);
    }

    @Test
    void writesATileIntoEverySlotItNames() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "<gold>Shop"
                rows = 3
                items {
                  filler { slots = ["0-8"], material = GRAY_STAINED_GLASS_PANE, name = " " }
                }
                """));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        Gui gui = draw().draw(spec, viewer);

        assertThat(gui.size()).isEqualTo(27);
        assertThat(plain(gui.title())).isEqualTo("Shop");
        assertThat(gui.getItem(0)).isNotNull();
        assertThat(gui.getItem(8)).isNotNull();
        assertThat(gui.getItem(9)).isNull();
    }

    @Test
    void asksTheWordsForANameTheFileWritesAsAKey() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "@shop.title"
                rows = 1
                items { one { slot = 0, material = STONE, name = "@shop.one" } }
                """));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();
        MenuDraw.Words catalogue = (who, written, values) -> Text.mini(
                switch (written) {
                    case "@shop.title" -> "The Shop";
                    case "@shop.one" -> "One stone";
                    default -> written;
                });

        Gui gui = draw(catalogue).draw(spec, viewer);

        assertThat(plain(gui.title())).isEqualTo("The Shop");
        assertThat(nameOf(gui, 0, viewer)).isEqualTo("One stone");
    }

    @Test
    void leavesTheSlotsOfAnItemWhoseConditionFails() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Shop"
                rows = 1
                items { hidden { slot = 4, material = STONE, view = ["is-staff"] } }
                """));
        conditions.register("is-staff", context -> false);
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        Gui gui = draw().draw(spec, viewer);

        assertThat(gui.getItem(4)).isNull();
    }

    @Test
    void theHighestPriorityWinsASharedSlot() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Shop"
                rows = 1
                items {
                  backdrop { slots = ["0-8"], material = STONE, name = "Backdrop", priority = 0 }
                  button   { slot = 4, material = DIAMOND, name = "Button", priority = 10 }
                }
                """));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        Gui gui = draw().draw(spec, viewer);

        assertThat(nameOf(gui, 4, viewer)).isEqualTo("Button");
        assertThat(nameOf(gui, 3, viewer)).isEqualTo("Backdrop");
    }

    @Test
    void runsTheSideOfTheClickTheFileWrote() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Shop"
                rows = 1
                items {
                  buy {
                    slot = 0, material = DIAMOND
                    click { left = ["shop:buy one"], right = ["shop:look one"] }
                  }
                }
                """));
        List<String> ran = new java.util.ArrayList<>();
        actions.registerVerb("shop:buy", (who, argument) -> ran.add("buy " + argument));
        actions.registerVerb("shop:look", (who, argument) -> ran.add("look " + argument));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        Gui gui = draw().draw(spec, viewer);
        click(gui, 0, viewer, ClickType.RIGHT);
        click(gui, 0, viewer, ClickType.SHIFT_LEFT);

        assertThat(ran).containsExactly("look one");
    }

    @Test
    void fillsAComputedListIntoItsOwnSlots() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Auction"
                rows = 2
                items {
                  backdrop { slots = ["0-17"], material = STONE, name = "Backdrop", priority = 0 }
                  rows {
                    slots = ["0-8"], priority = 5
                    list {
                      source = "auction:open"
                      template { material = "%icon%", name = "%seller%" }
                    }
                  }
                }
                """));
        lists.register(
                "auction:open",
                viewer -> List.of(
                        MenuLists.Row.of(Map.of("%icon%", "DIAMOND", "%seller%", "Ada")),
                        MenuLists.Row.of(new ItemStack(Material.GOLD_INGOT), Map.of("%seller%", "Kerem"))));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        Gui gui = draw().draw(spec, viewer);

        assertThat(gui).isInstanceOf(PaginatedGui.class);
        assertThat(nameOf(gui, 0, viewer)).isEqualTo("Ada");
        assertThat(nameOf(gui, 1, viewer)).isEqualTo("Kerem");
        GuiItem second = java.util.Objects.requireNonNull(gui.getItem(1));
        assertThat(second.icon(new RenderContext(viewer, gui, 1)).getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(nameOf(gui, 9, viewer)).isEqualTo("Backdrop");
    }

    @Test
    void aPagingButtonTurnsThePage() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Auction"
                rows = 1
                items {
                  rows {
                    slots = ["0-1"], priority = 5
                    list { source = "auction:open", template { material = STONE, name = "%who%" } }
                  }
                  next { slot = 8, material = ARROW, type = next, priority = 10 }
                }
                """));
        lists.register(
                "auction:open",
                viewer -> List.of(
                        MenuLists.Row.of(Map.of("%who%", "one")),
                        MenuLists.Row.of(Map.of("%who%", "two")),
                        MenuLists.Row.of(Map.of("%who%", "three"))));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        Gui gui = draw().draw(spec, viewer);
        assertThat(nameOf(gui, 0, viewer)).isEqualTo("one");

        click(gui, 8, viewer, ClickType.LEFT);

        assertThat(nameOf(gui, 0, viewer)).isEqualTo("three");
    }

    @Test
    void refusesAMaterialNoServerHas() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Shop"
                rows = 1
                items { odd { slot = 0, material = "NOT_A_STONE" } }
                """));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        assertThatThrownBy(() -> draw().draw(spec, viewer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("odd")
                .hasMessageContaining("NOT_A_STONE");
    }

    @Test
    void refusesAListNobodyRegistered() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Auction"
                rows = 1
                items {
                  rows {
                    slots = ["0-8"]
                    list { source = "auction:open", template { material = STONE } }
                  }
                }
                """));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        assertThatThrownBy(() -> draw().draw(spec, viewer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auction:open");
    }

    @Test
    void runsWhatTheFileDoesOnOpening() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "Shop"
                rows = 1
                open-actions = ["shop:opened now"]
                items { one { slot = 0, material = STONE } }
                """));
        List<String> ran = new java.util.ArrayList<>();
        actions.registerVerb("shop:opened", (who, argument) -> ran.add(argument));
        PlayerMock viewer = MockBukkit.getMock().addPlayer();

        draw().open(spec, viewer);

        assertThat(ran).containsExactly("now");
    }
}
