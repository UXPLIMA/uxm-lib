package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/** The pass over a whole catalog, and the palette swap that has to reach everything already holding it. */
class StylerTest {

    private static final MessageKey WELCOME = MessageKey.of("join.welcome", "<body>Welcome");

    /**
     * A theme that writes English in small capitals, which is what these tests are about. The library
     * converts nothing until a file names a language, so the file names one here.
     */
    private static Theme smallCapsInEnglish() {
        ConfigurationNode node = CommentedConfigurationNode.root();
        try {
            node.node("small-caps", "en").set(true);
        } catch (ConfigurateException cannotHappen) {
            throw new AssertionError(cannotHappen);
        }
        return Theme.from(node);
    }

    @Test
    void aTemplateIsStyledForTheLanguageItIsRenderedIn() {
        Styler styler = new Styler(smallCapsInEnglish());

        assertThat(styler.apply("<body>Welcome", Locale.ENGLISH)).isEqualTo("<color:#ffffff>ᴡᴇʟᴄᴏᴍᴇ");
        assertThat(styler.apply("<body>Hoş geldin", Locale.of("tr"))).isEqualTo("<color:#ffffff>Hoş geldin");
    }

    @Test
    void everyKeyIsStyledForEveryLanguageIncludingTheCompiledDefault() {
        Styler styler = new Styler(smallCapsInEnglish());
        Map<Locale, Map<String, String>> files = Map.of(Locale.of("tr"), Map.of("join.welcome", "<body>Hoş geldin"));
        MessageCatalog source = new MessageCatalog(files, Locale.ENGLISH);

        MessageCatalog styled = styler.style(source, List.of(WELCOME), files, Locale.ENGLISH);

        assertThat(styled.template(WELCOME, Locale.ENGLISH)).isEqualTo("<color:#ffffff>ᴡᴇʟᴄᴏᴍᴇ");
        assertThat(styled.template(WELCOME, Locale.of("tr"))).isEqualTo("<color:#ffffff>Hoş geldin");
    }

    /** A line an operator added that no key declares is styled too, or their line is the odd one out. */
    @Test
    void aPathTheKeysDoNotKnowIsStyledFromTheFile() {
        Styler styler = new Styler(smallCapsInEnglish());
        Map<Locale, Map<String, String>> files = Map.of(Locale.ENGLISH, Map.of("their.own.line", "<body>Mine"));
        MessageCatalog source = new MessageCatalog(files, Locale.ENGLISH);

        MessageCatalog styled = styler.style(source, List.of(WELCOME), files, Locale.ENGLISH);

        MessageKey theirs = MessageKey.of("their.own.line", "<body>Mine");
        assertThat(styled.template(theirs, Locale.ENGLISH)).isEqualTo("<color:#ffffff>ᴍɪɴᴇ");
    }

    /**
     * The reason a styler is held rather than rebuilt: a menu asks for the theme every time it draws, so a
     * palette that arrives through the styler repaints the open menus with the chat instead of after it.
     */
    @Test
    void aNewPaletteReachesEverythingAlreadyHoldingTheStyler() throws ConfigurateException {
        Styler styler = new Styler(smallCapsInEnglish());
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("colours", "body").set("#ff0000");
        node.node("small-caps", "en").set(true);

        styler.reload(Theme.from(node));

        assertThat(styler.theme().hex("body")).isEqualTo("#ff0000");
        assertThat(styler.apply("<body>Welcome", Locale.ENGLISH)).isEqualTo("<color:#ff0000>ᴡᴇʟᴄᴏᴍᴇ");
    }
}
