package com.uxplima.uxmlib.gui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading the lines a menu file writes under {@code click} and {@code open-actions}.
 *
 * <p>A line nobody can run is refused here, while the file loads, because the alternative is a button that
 * does nothing under a player's cursor and an operator who has no way to see why.
 */
class MenuActionTest {

    private static final Set<String> KNOWN = Set.of("shop:buy");

    @Test
    @DisplayName("the five built-in verbs are read")
    void readsTheBuiltIns() {
        assertThat(read("close")).isEqualTo(new MenuAction.Close());
        assertThat(read("open:categories")).isEqualTo(new MenuAction.OpenMenu("categories"));
        assertThat(read("command:auction sell 250")).isEqualTo(new MenuAction.RunCommand("auction sell 250"));
        assertThat(read("message:<green>Done.")).isEqualTo(new MenuAction.SendMessage("<green>Done."));
    }

    @Test
    @DisplayName("a verb is read whatever its case, and the argument keeps its own")
    void readsTheVerbInAnyCase() {
        assertThat(read("OPEN:Categories")).isEqualTo(new MenuAction.OpenMenu("Categories"));
        assertThat(read("Close")).isEqualTo(new MenuAction.Close());
    }

    @Test
    @DisplayName("a sound takes a volume and a pitch, and one is 1.0 when it is not written")
    void readsASound() {
        assertThat(read("sound:item.book.page_turn 0.7 1.2"))
                .isEqualTo(new MenuAction.PlaySound("item.book.page_turn", 0.7F, 1.2F));
        assertThat(read("sound:block.note_block.pling"))
                .isEqualTo(new MenuAction.PlaySound("block.note_block.pling", 1.0F, 1.0F));
        assertThat(read("sound:block.note_block.pling 0.5"))
                .isEqualTo(new MenuAction.PlaySound("block.note_block.pling", 0.5F, 1.0F));
    }

    @Test
    @DisplayName("the constant form of a sound is read, because the menu files that exist write it")
    void readsTheConstantFormOfASound() {
        assertThat(read("sound:ITEM_BOOK_PAGE_TURN 0.7 1.2"))
                .isEqualTo(new MenuAction.PlaySound("ITEM_BOOK_PAGE_TURN", 0.7F, 1.2F));
    }

    @Test
    @DisplayName("a sound that is neither a key nor a constant is refused, and the message says both forms")
    void refusesASoundThatIsNeitherForm() {
        assertThatThrownBy(() -> read("sound:Item Book"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("item.book.page_turn")
                .hasMessageContaining("ITEM_BOOK_PAGE_TURN");
    }

    @Test
    @DisplayName("a volume that is not a number is refused")
    void refusesAVolumeThatIsNotANumber() {
        assertThatThrownBy(() -> read("sound:item.book.page_turn loud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    @DisplayName("a name a plugin registered is read, with the rest of the line as its argument")
    void readsANamedAction() {
        assertThat(read("shop:buy diamond 4")).isEqualTo(new MenuAction.Named("shop:buy", "diamond 4"));
        assertThat(read("shop:buy")).isEqualTo(new MenuAction.Named("shop:buy", ""));
    }

    @Test
    @DisplayName("a name nobody registered is refused as the file loads")
    void refusesAnUnknownName() {
        assertThatThrownBy(() -> read("shop:sell diamond"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shop:sell diamond")
                .hasMessageContaining("close");
    }

    @Test
    @DisplayName("a word with no colon is refused, because every named action holds one")
    void refusesAWordWithNoColon() {
        assertThatThrownBy(() -> read("buy")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a built-in with nothing after it is refused, and the message names the verb")
    void refusesAVerbWithNoArgument() {
        assertThatThrownBy(() -> read("open:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open");
        assertThatThrownBy(() -> read("command:  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an empty line is refused")
    void refusesAnEmptyLine() {
        assertThatThrownBy(() -> read("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a whole list is read in the order the file wrote it")
    void readsAWholeList() {
        List<MenuAction> lines = MenuAction.readAll(
                List.of("message:<green>Bought.", "sound:block.note_block.pling", "close"), KNOWN::contains);

        assertThat(lines)
                .containsExactly(
                        new MenuAction.SendMessage("<green>Bought."),
                        new MenuAction.PlaySound("block.note_block.pling", 1.0F, 1.0F),
                        new MenuAction.Close());
    }

    private static MenuAction read(String line) {
        return MenuAction.read(line, KNOWN::contains);
    }
}
