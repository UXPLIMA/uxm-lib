package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers the clickable, paginated {@code /help}: the generated help node accepts a {@code [page]} argument,
 * and the rendered page carries clickable lines (a suggest-command per branch) plus a previous/next footer
 * when the branch list spans more than one page. The rendering is checked through the pure
 * {@link HelpRenderer#render} so it needs no live sender; the node shape is checked off the built tree.
 */
class HelpPaginationTest {

    @Command(name = "town")
    static class TownCommand {
        @Subcommand(value = "create", description = "Found a town")
        void create(Sender sender) {}

        @Subcommand(value = "delete", description = "Disband your town")
        void delete(Sender sender) {}
    }

    private static List<HelpRenderer.Entry> many(int count) {
        List<HelpRenderer.Entry> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new HelpRenderer.Entry("sub" + i, "branch " + i, ""));
        }
        return list;
    }

    @Test
    void helpNodeTakesAPageArgument() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new TownCommand());
        CommandNode<CommandSourceStack> help = node.getChild("help");
        assertThat(help).isNotNull();
        assertThat(java.util.Objects.requireNonNull(help).getCommand()).isNotNull(); // /town help runs page 1
        CommandNode<CommandSourceStack> page =
                java.util.Objects.requireNonNull(help).getChild("page");
        assertThat(page).isNotNull();
        assertThat(java.util.Objects.requireNonNull(page).getCommand()).isNotNull();
    }

    @Test
    void eachLineSuggestsItsCommandOnClick() {
        List<HelpRenderer.Entry> entries = List.of(new HelpRenderer.Entry("create", "Found a town", ""));
        Component page = HelpRenderer.render("town", entries, 1, HelpRenderer.PER_PAGE);
        assertThat(hasClick(page, ClickEvent.suggestCommand("/town create"))).isTrue();
    }

    @Test
    void aMultiPageListShowsANextButton() {
        Component page1 = HelpRenderer.render("town", many(20), 1, HelpRenderer.PER_PAGE);
        String text = PlainTextComponentSerializer.plainText().serialize(page1);
        assertThat(text).contains("(1/3)").contains("next");
        // The next button runs the help command for page 2.
        assertThat(hasClick(page1, ClickEvent.runCommand("/town help 2"))).isTrue();
    }

    @Test
    void thePageHeaderCountsTheVisiblePages() {
        Component lastPage = HelpRenderer.render("town", many(20), 3, HelpRenderer.PER_PAGE);
        String text = PlainTextComponentSerializer.plainText().serialize(lastPage);
        assertThat(text).contains("(3/3)").contains("prev");
    }

    @Test
    void aSinglePageHasNoFooter() {
        Component page = HelpRenderer.render("town", many(2), 1, HelpRenderer.PER_PAGE);
        assertThat(hasClick(page, ClickEvent.runCommand("/town help 2"))).isFalse();
    }

    @Test
    void theHelpChromeIsWordedByTheMessagesItIsGiven() {
        CommandMessages messages = new CommandMessages() {
            @Override
            public Component helpHeader(Locale locale, String command, int page, int pages) {
                return Component.text("/" + command + " yardım (" + page + "/" + pages + ")");
            }

            @Override
            public Component helpFillHint(Locale locale) {
                return Component.text("Doldurmak için tıkla");
            }

            @Override
            public Component helpPageHint(Locale locale, int page) {
                return Component.text("Sayfa " + page);
            }
        };
        List<HelpRenderer.Entry> entries = new java.util.ArrayList<>(many(20));
        entries.add(0, new HelpRenderer.Entry("create", "", "")); // no description: the hover is the library's

        Locale turkish = Locale.forLanguageTag("tr");

        Component page = HelpRenderer.render("town", entries, 1, HelpRenderer.PER_PAGE, messages, turkish);

        String text = PlainTextComponentSerializer.plainText().serialize(page);
        assertThat(text).startsWith("/town yardım (1/3)");
        assertThat(hasHover(page, HoverEvent.showText(Component.text("Doldurmak için tıkla")))).isTrue();
        assertThat(hasHover(page, HoverEvent.showText(Component.text("Sayfa 2")))).isTrue();
    }

    @Test
    void aPageRenderedWithoutAMessageLayerKeepsTheEnglishChrome() {
        Component page = HelpRenderer.render("town", many(20), 1, HelpRenderer.PER_PAGE);

        String text = PlainTextComponentSerializer.plainText().serialize(page);
        assertThat(text).startsWith("/town help (1/3)");
        assertThat(hasHover(page, HoverEvent.showText(Component.text("Page 2")))).isTrue();
    }

    /** Whether any component in the tree carries exactly {@code expected} as its hover event. */
    private static boolean hasHover(Component component, Object expected) {
        if (expected.equals(component.hoverEvent())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasHover(child, expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any component in the tree carries exactly {@code expected} as its click event. Comparing whole
     * events rather than reading a value back off one keeps this readable across Adventure lines: 5.x gave
     * ClickEvent a type parameter and swapped {@code value()} for a typed payload, while the factories and
     * {@code equals} behave identically on both. {@code Object} for the same reason — a declared
     * {@code ClickEvent} would be a raw type on 5.x and an over-specified one on 4.x.
     */
    private static boolean hasClick(Component component, Object expected) {
        if (expected.equals(component.clickEvent())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasClick(child, expected)) {
                return true;
            }
        }
        return false;
    }
}
