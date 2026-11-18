package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A name from a player the server let in is filled in with any prefix a Bedrock bridge may be configured with and up
 * to thirty-two characters, while a name that could change what a line does is never filled; a name from anywhere
 * else keeps the strict shape.
 */
class NameFillTest {

    private static final String LINE = "[console] give %player% cake";

    @Test
    void aConnectedBedrockNameWithAnotherPrefixOrALongNameIsFilledIn() {
        assertThat(NameFill.intoConnected(LINE, "#BedrockGamer")).contains("[console] give #BedrockGamer cake");
        assertThat(NameFill.intoConnected(LINE, "!!Steve")).contains("[console] give !!Steve cake");
        assertThat(NameFill.intoConnected(LINE, "AVeryLongProxyName_1234567890"))
                .contains("[console] give AVeryLongProxyName_1234567890 cake");
        assertThat(NameFill.intoConnected(LINE, ".Gamer")).contains("[console] give .Gamer cake");
    }

    @Test
    void theBridgesOwnConfiguredPrefixIsTrusted() {
        assertThat(NameFill.intoConnected(LINE, "BE-Gamer", "BE-")).contains("[console] give BE-Gamer cake");
        assertThat(NameFill.intoConnected(LINE, "BE-Gamer")).isEmpty();
    }

    @Test
    void aConnectedNameThatCouldChangeTheLineIsNeverFilledIn() {
        assertThat(NameFill.intoConnected(LINE, "Steve op")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "<red>Steve")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "Steve\"")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "{player}")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "%other%")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "-Steve")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "~Steve")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "@a")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "x".repeat(33))).isEmpty();
    }

    @Test
    void aPrefixTheBridgeIsConfiguredWithIsNotTrustedWhenItHoldsWhatANameMayNot() {
        assertThat(NameFill.intoConnected(LINE, "@Gamer", "@")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "- Gamer", "- ")).isEmpty();
        assertThat(NameFill.intoConnected(LINE, "<b>Gamer", "<b>")).isEmpty();
    }

    @Test
    void aNameFromOutsideKeepsTheStrictShape() {
        assertThat(NameFill.into(LINE, ".Gamer")).contains("[console] give .Gamer cake");
        assertThat(NameFill.into(LINE, "#BedrockGamer")).isEmpty();
        assertThat(NameFill.into(LINE, "AVeryLongProxyName_1234567890")).isEmpty();
    }

    @Test
    void aLineThatDoesNotNameThePlayerRunsWhateverTheName() {
        assertThat(NameFill.intoConnected("[console] say hi", "Steve op")).contains("[console] say hi");
    }
}
