package com.uxplima.uxmlib.gui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reading a menu file, with no server behind it.
 *
 * <p>A layout that cannot be drawn is refused here and named, because the alternative is a button the
 * server silently drops and an operator who cannot see why.
 */
class MenuSpecTest {

    @Test
    @DisplayName("a whole menu is read: the title, the rows, the open actions and every item")
    void readsAWholeMenu() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "@shop.title"
                rows = 6
                open-actions = ["sound:ITEM_BOOK_PAGE_TURN 0.7 1.2"]

                items {
                  filler {
                    slots    = ["45-47", "51-53"]
                    material = GRAY_STAINED_GLASS_PANE
                    name     = ""
                    priority = 0
                  }

                  rows {
                    slots    = ["0-44"]
                    priority = 5
                    list {
                      source = "shop:offers"
                      template {
                        material = "%offer_icon%"
                        name     = "@shop.offer.name"
                        lore     = ["@shop.offer.lore"]
                        click { left = ["shop:buy"] }
                      }
                    }
                  }

                  previous {
                    slot     = 48
                    type     = PREVIOUS
                    material = ARROW
                    name     = "@shop.previous"
                    priority = 10
                  }

                  close {
                    slot     = 49
                    material = BARRIER
                    name     = "@shop.close"
                    priority = 10
                    view     = ["may-close"]
                    click {
                      left        = ["close"]
                      right       = ["shop:home", "close"]
                      shift_left  = ["shop:home"]
                      shift_right = ["message:<gray>Nothing here."]
                    }
                  }
                }
                """));

        assertThat(spec.title()).isEqualTo("@shop.title");
        assertThat(spec.rows()).isEqualTo(6);
        assertThat(spec.capacity()).isEqualTo(54);
        assertThat(spec.openActions()).containsExactly("sound:ITEM_BOOK_PAGE_TURN 0.7 1.2");

        MenuSpec.Item filler = itemOf(spec, "filler");
        assertThat(filler.slots()).hasSize(6).contains(45, 47, 51, 53).doesNotContain(48, 49, 50);

        MenuSpec.Item rows = itemOf(spec, "rows");
        assertThat(rows.slots()).hasSize(45);
        MenuSpec.Listing offers = Objects.requireNonNull(rows.list(), "the list of the rows item");
        assertThat(offers.source()).isEqualTo("shop:offers");
        assertThat(offers.template().material()).isEqualTo("%offer_icon%");
        assertThat(offers.template().click().left()).containsExactly("shop:buy");

        assertThat(itemOf(spec, "previous").kind()).isEqualTo(MenuSpec.Kind.PREVIOUS);

        MenuSpec.Item close = itemOf(spec, "close");
        assertThat(close.view()).containsExactly("may-close");
        assertThat(close.click().left()).containsExactly("close");
        assertThat(close.click().right()).containsExactly("shop:home", "close");
        assertThat(close.click().shiftLeft()).containsExactly("shop:home");
        assertThat(close.click().shiftRight()).containsExactly("message:<gray>Nothing here.");
    }

    @Test
    @DisplayName("the items come back in the order they are drawn, lowest priority first")
    void theBackdropIsDrawnFirst() throws Exception {
        MenuSpec spec = MenuSpec.read(
                parse(
                        """
                title = "t"
                rows = 1
                items {
                  button  { slot = 0, priority = 10 }
                  backdrop { slots = ["0-8"], priority = 0 }
                }
                """));

        assertThat(spec.items().stream().map(MenuSpec.Item::id)).containsExactly("backdrop", "button");
    }

    @Test
    @DisplayName("an item with no click and no list is a tile that only shows something")
    void aPlainTileIsAllowed() throws Exception {
        MenuSpec.Item only = itemOf(
                MenuSpec.read(
                        parse(
                                """
                        title = "t"
                        rows = 1
                        items { info { slot = 4, material = PAPER } }
                        """)),
                "info");

        assertThat(only.kind()).isEqualTo(MenuSpec.Kind.PLAIN);
        assertThat(only.click().isEmpty()).isTrue();
        assertThat(only.list()).isNull();
        assertThat(only.name()).isNull();
        assertThat(only.lore()).isEmpty();
    }

    @Test
    @DisplayName("a slot the window does not hold is refused, and the item is named")
    void aSlotOutsideTheWindowIsRefused() {
        assertThatThrownBy(
                        () -> MenuSpec.read(
                                parse(
                                        """
                        title = "t"
                        rows = 1
                        items { away { slot = 9 } }
                        """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("away")
                .hasMessageContaining("9 slots");
    }

    @Test
    @DisplayName("a range the wrong way round is refused")
    void aBackwardsRangeIsRefused() {
        assertThatThrownBy(
                        () -> MenuSpec.read(
                                parse(
                                        """
                        title = "t"
                        rows = 1
                        items { grid { slots = ["8-0"] } }
                        """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the wrong way round");
    }

    @Test
    @DisplayName("an item that names no slot, or both kinds of slot, is refused")
    void anItemNamesOneKindOfSlot() {
        assertThatThrownBy(
                        () -> MenuSpec.read(
                                parse(
                                        """
                        title = "t"
                        rows = 1
                        items { lost { material = PAPER } }
                        """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no slot");

        assertThatThrownBy(
                        () -> MenuSpec.read(
                                parse(
                                        """
                        title = "t"
                        rows = 1
                        items { both { slot = 1, slots = ["2-3"] } }
                        """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }

    @Test
    @DisplayName("a menu with no title, or with rows a chest cannot show, is refused")
    void theWindowItselfIsChecked() {
        assertThatThrownBy(() -> MenuSpec.read(parse("rows = 1\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no title");

        assertThatThrownBy(() -> MenuSpec.read(parse("title = \"t\"\nrows = 7\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 to 6 rows");
    }

    @Test
    @DisplayName("a type the engine does not know is refused by name")
    void anUnknownTypeIsRefused() {
        assertThatThrownBy(
                        () -> MenuSpec.read(
                                parse(
                                        """
                        title = "t"
                        rows = 1
                        items { odd { slot = 0, type = SIDEWAYS } }
                        """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIDEWAYS")
                .hasMessageContaining("plain, previous or next");
    }

    private static MenuSpec.Item itemOf(MenuSpec spec, String id) {
        return spec.items().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no item called " + id));
    }

    private static ConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
    }

    @Test
    @DisplayName("a slots list may hold single numbers beside ranges, and never repeats a slot")
    void slotsMayMixNumbersAndRanges() throws Exception {
        MenuSpec.Item item = itemOf(
                MenuSpec.read(
                        parse(
                                """
                        title = "t"
                        rows = 1
                        items { mixed { slots = ["0-2", "4", "1-2"] } }
                        """)),
                "mixed");

        assertThat(item.slots()).isEqualTo(List.of(0, 1, 2, 4));
    }
}
