package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A line an operator wrote into an effect list takes the theme's roles and labels, and keeps its own letters.
 *
 * <p>{@link Styler#apply} writes a whole English template in small capitals, which is right for a catalogue line and
 * wrong for a line that carries a placeholder: {@code %player_name%} became {@code %ᴘʟᴀʏᴇʀ_ɴᴀᴍᴇ%} and was never
 * filled in. {@link Styler#tokens} paints the roles and the label and leaves every other letter as written.
 */
class ALineAnOperatorWroteKeepsItsLettersTest {

    private final Styler styler = new Styler(Theme.defaults());

    @Test
    @DisplayName("the roles and the label are painted and a placeholder keeps its letters")
    void rolesArePaintedAndPlaceholdersKeepTheirLetters() {
        String line = styler.tokens("<tag:'JOBS'> <body>Thanks, %player_name%.", Locale.ENGLISH);

        assertThat(line).doesNotContain("<tag:", "<body>").contains("<color:", "JOBS", "Thanks, %player_name%.");
    }

    @Test
    @DisplayName("a label follows the language, and Turkish keeps its capitals")
    void aLabelFollowsTheLanguage() {
        String line = styler.tokens("<tag:'İŞLER'> <body>Sağ ol.", Locale.forLanguageTag("tr"));

        assertThat(line).contains("İŞLER", "Sağ ol.").doesNotContain("<tag:");
    }
}
