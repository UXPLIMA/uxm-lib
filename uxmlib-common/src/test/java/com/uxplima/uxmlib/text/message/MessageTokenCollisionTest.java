package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A name that is both a colour role and a value, on the one road where the value cannot be made to win.
 *
 * <p>A catalogue is styled once when the plugin loads. No value exists at that moment, so a bare
 * {@code <level>} is painted with the {@code level} role of {@code theme.conf} and consumed, and the resolver
 * that arrives at render time meets nothing to fill. The line still reads like a line: {@code Level  of 100}.
 * uxmSkills lost seven lines that way, uxmMinions twenty, and six defaults in uxmSkills' own key enum were
 * still doing it days later, because nothing anywhere said a word.
 *
 * <p>What is fixed here is the silence. The style pass writes down which role tokens it ate that a value could
 * have been meant by, and this facade holds those against the values a caller actually supplies.
 */
class MessageTokenCollisionTest {

    private static final MessageKey TILE = MessageKey.of("skill.tile", "Level <level> of 100");

    private final List<String> logged = new java.util.ArrayList<>();

    private final Logger log = Logger.getLogger(Messages.class.getName());

    private Handler capture;

    @BeforeEach
    void setUp() {
        capture = new Handler() {

            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        log.addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        log.removeHandler(capture);
    }

    private static Messages facade(java.util.function.Predicate<String> supplied) {
        Map<Locale, Map<String, String>> files = Map.of();
        MessageCatalog styled = new Styler(Theme.defaults())
                .style(new MessageCatalog(files, Locale.ENGLISH), List.of(TILE), files, Locale.ENGLISH, supplied);
        return new Messages(styled, LocaleSource.ofDefault(Locale.ENGLISH));
    }

    @Test
    @DisplayName("a value that cannot reach the line it was meant for is reported, with the path and the name")
    void reportsAValueThatCannotReachItsLine() {
        Messages messages = facade(name -> false);

        messages.render(Audience.empty(), TILE, Text.placeholder("level", "7"));

        assertThat(logged).hasSize(1);
        assertThat(logged.get(0)).contains("skill.tile").contains("<level>").contains("theme.conf");
    }

    /** A defect is worth saying once. A line drawn on every tile of a menu must not fill the log with it. */
    @Test
    @DisplayName("the same collision is reported once and not on every render")
    void reportsOneCollisionOnce() {
        Messages messages = facade(name -> false);

        messages.render(Audience.empty(), TILE, Text.placeholder("level", "7"));
        messages.render(Audience.empty(), TILE, Text.placeholder("level", "8"));

        assertThat(logged).hasSize(1);
    }

    /** Nothing is said where nothing collided: a line rendered with no value of that name is not a defect. */
    @Test
    @DisplayName("a line rendered with no value of that name says nothing")
    void staysQuietWhenNoValueCollides() {
        Messages messages = facade(name -> false);

        messages.render(Audience.empty(), TILE, Text.placeholder("coins", "7"));
        messages.render(Audience.empty(), TILE);

        assertThat(logged).isEmpty();
    }

    /**
     * The other half. A plugin that names its own values to the style pass keeps the token, so the number
     * arrives and there is nothing to report.
     */
    @Test
    @DisplayName("a plugin that names its values keeps the number, and nothing is reported")
    void aNamedValueReachesTheLine() {
        Messages messages = facade("level"::equals);

        String drawn = Text.plain(messages.render(Audience.empty(), TILE, Text.placeholder("level", "7")));

        assertThat(drawn).isEqualTo("Level 7 of 100");
        assertThat(logged).isEmpty();
    }

    /** What the estate saw, and what the report now names: the number is gone and the line still reads. */
    @Test
    @DisplayName("the line a swallowed value leaves behind is a line that still reads like a line")
    void theSwallowedValueLeavesASentenceBehind() {
        Messages messages = facade(name -> false);

        String drawn = Text.plain(messages.render(Audience.empty(), TILE, Text.placeholder("level", "7")));

        assertThat(drawn).isEqualTo("Level  of 100");
        assertThat(logged).hasSize(1);
    }
}
