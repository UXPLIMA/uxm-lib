package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("A player's name is filled into a line only where it cannot change what the line does")
class NameFillTest {

    @ParameterizedTest
    @ValueSource(strings = {"Alex", "alex_99", "A", "Sixteen_letters_", ".Steve", "*Bedrock_Name"})
    @DisplayName("a name an account can have, a bridge prefix included, is an account name")
    void accountNames(String name) {
        assertThat(NameFill.isAccountName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "x<red>", "Seventeen_letters", "semi;colon", "quote\"d", "back\\slash", ".."})
    @DisplayName("a name with a space, a bracket, a quote or too many letters is not")
    void otherNames(String name) {
        assertThat(NameFill.isAccountName(name)).isFalse();
    }

    @Test
    @DisplayName("every spelling of the name is filled, in a command and in a text")
    void everySpellingIsFilled() {
        assertThat(NameFill.intoEffect("[console] give %player% cake", "Alex")).contains("[console] give Alex cake");
        assertThat(NameFill.intoEffect("[message] hi <player> and {player}", "Alex"))
                .contains("[message] hi Alex and Alex");
        assertThat(NameFill.intoCommand("give {player} cake", "Alex")).contains("give Alex cake");
    }

    @Test
    @DisplayName("a name that is not an account's never reaches a command: the line is skipped")
    void aStrangeNameNeverReachesACommand() {
        assertThat(NameFill.intoEffect("[console] give %player% cake", "a b")).isEmpty();
        assertThat(NameFill.intoEffect("  [PLAYER] {delay=20} spawn <player>", "x<red>"))
                .isEmpty();
        assertThat(NameFill.intoCommand("give %player% cake", "a b")).isEmpty();
    }

    @Test
    @DisplayName("a name that is not an account's reaches a text as text, never as markup")
    void aStrangeNameIsTextInAText() {
        assertThat(NameFill.intoEffect("[message] hi <player>", "x<red>")).contains("[message] hi x\\<red>");
        assertThat(NameFill.intoEffect("[title] {player} | welcome", "x<red>")).contains("[title] x\\<red> | welcome");
    }

    @Test
    @DisplayName("a line that does not name the player is left as it is, whatever the name")
    void aLineWithoutTheNameIsUnchanged() {
        assertThat(NameFill.intoEffect("[console] say hello", "a b")).contains("[console] say hello");
        assertThat(NameFill.intoCommand("say hello", "a b")).contains("say hello");
    }

    @Test
    @DisplayName("a line the engine will refuse anyway is not a command, so the name reaches it escaped")
    void anUnknownVerbIsNotACommand() {
        assertThat(NameFill.intoEffect("[shout] <player>", "x<red>")).contains("[shout] x\\<red>");
        assertThat(NameFill.intoEffect("hi <player>", "x<red>")).contains("hi x\\<red>");
    }
}
