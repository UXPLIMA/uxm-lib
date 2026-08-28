package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void parsesMiniMessageAndFlattensToPlain() {
        Component parsed = Text.mini("<red>Hello</red>");
        assertThat(Text.plain(parsed)).isEqualTo("Hello");
    }

    @Test
    void resolvesAnUnparsedPlaceholderLiterally() {
        Component parsed = Text.mini("Hi <name>!", Text.placeholder("name", "<bold>Steve"));
        // unparsed: the value's tags are shown as text, never parsed.
        assertThat(Text.plain(parsed)).isEqualTo("Hi <bold>Steve!");
    }

    @Test
    void resolvesAComponentPlaceholder() {
        Component parsed = Text.mini("Welcome <who>", Text.component("who", Component.text("Alice")));
        assertThat(Text.plain(parsed)).isEqualTo("Welcome Alice");
    }

    @Test
    void paintsAComponentWithoutParsingIt() {
        Component painted = Text.paint(Component.text("hi"), "<red>");
        assertThat(Text.plain(painted)).isEqualTo("hi");
        assertThat(firstColor(painted)).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void paintedTextNeverBecomesAClickEvent() {
        // The shape of the exploit: a player types a tag and expects the server to run it for them.
        String hostile = "<click:run_command:/op me>free rank</click>";
        Component painted = Text.paint(hostile, "<gray>");
        assertThat(Text.plain(painted)).isEqualTo(hostile);
        assertThat(hasClickEvent(painted)).isFalse();
    }

    @Test
    void paintedTextSurvivesALegacySectionSign() {
        // Fed to MiniMessage this throws ("Legacy formatting codes have been detected") and eats the message.
        Component painted = Text.paint("\u00a7cred", "<gray>");
        assertThat(Text.plain(painted)).isEqualTo("\u00a7cred");
    }

    private static TextColor firstColor(Component component) {
        if (component.color() != null) {
            return component.color();
        }
        for (Component child : component.children()) {
            TextColor found = firstColor(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean hasClickEvent(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(TextTest::hasClickEvent);
    }

    @Test
    void stripsTags() {
        assertThat(Text.stripTags("<green>hello <bold>world</bold></green>")).isEqualTo("hello world");
    }
}
