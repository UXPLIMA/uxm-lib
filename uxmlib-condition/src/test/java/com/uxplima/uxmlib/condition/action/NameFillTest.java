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
    @ValueSource(
            strings = {
                "",
                "a b",
                "x<red>",
                "Seventeen_letters",
                "semi;colon",
                "quote\"d",
                "back\\slash",
                "..",
                "x'y",
                "a{b}",
                "a|b",
                "x%player%"
            })
    @DisplayName("a name with a space, a bracket, a quote, a brace or too many letters is not")
    void otherNames(String name) {
        assertThat(NameFill.isAccountName(name)).isFalse();
    }

    @Test
    @DisplayName("every spelling of the name is filled, in a command and in a text")
    void everySpellingIsFilled() {
        assertThat(NameFill.into("[console] give %player% cake", "Alex")).contains("[console] give Alex cake");
        assertThat(NameFill.into("[message] hi <player> and {player}", "Alex")).contains("[message] hi Alex and Alex");
        assertThat(NameFill.into("give {player} cake", "Alex")).contains("give Alex cake");
    }

    @Test
    @DisplayName("a name that is not an account's never reaches a command: the line is skipped")
    void aStrangeNameNeverReachesACommand() {
        assertThat(NameFill.into("[console] give %player% cake", "a b")).isEmpty();
        assertThat(NameFill.into("  [PLAYER] {delay=20} spawn <player>", "x<red>"))
                .isEmpty();
        assertThat(NameFill.into("give %player% cake", "a b")).isEmpty();
    }

    @Test
    @DisplayName("nor a text, where it could close a quoted tag argument or open a modifier: the line is skipped")
    void aStrangeNameNeverReachesAText() {
        assertThat(NameFill.into("[message] hi <player>", "x<red>")).isEmpty();
        assertThat(NameFill.into("[message] <click:run_command:'/msg <player>'>reply", "x' y"))
                .isEmpty();
        assertThat(NameFill.into("[title] {player} | welcome", "a|b")).isEmpty();
        assertThat(NameFill.into("hi <player>", "a{delay=1}")).isEmpty();
    }

    @Test
    @DisplayName("a line that does not name the player is left as it is, whatever the name")
    void aLineWithoutTheNameIsUnchanged() {
        assertThat(NameFill.into("[console] say hello", "a b")).contains("[console] say hello");
        assertThat(NameFill.into("[message] hello", "x<red>")).contains("[message] hello");
    }
}
