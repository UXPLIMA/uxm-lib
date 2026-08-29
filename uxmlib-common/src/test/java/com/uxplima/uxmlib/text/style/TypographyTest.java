package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The letters pass: what it converts, what it must not touch, and what the plain markers do. */
class TypographyTest {

    @Test
    void lettersBecomeSmallCapitalsAndEverythingElseIsLeftAlone() {
        assertThat(Typography.apply("Page 3 of 50", true)).isEqualTo("ᴘᴀɢᴇ 3 ᴏꜰ 50");
    }

    @Test
    void aTagAndAPlaceholderAreCopiedThroughUntouched() {
        assertThat(Typography.apply("<body>You are <value><player></value>", true))
                .isEqualTo("<body>ʏᴏᴜ ᴀʀᴇ <value><player></value>");
    }

    @Test
    void textInsidePlainMarkersKeepsOrdinaryLettersAndTheMarkersAreRemoved() {
        assertThat(Typography.apply("version <plain>1.2.0-beta</plain> here", true))
                .isEqualTo("ᴠᴇʀꜱɪᴏɴ 1.2.0-beta ʜᴇʀᴇ");
    }

    @Test
    void aLanguageWithoutSmallCapitalsKeepsItsLettersAndStillLosesTheMarkers() {
        assertThat(Typography.apply("Etiketin <plain>/tag</plain> ile açık", false))
                .isEqualTo("Etiketin /tag ile açık");
    }

    @Test
    void aBracketInsideAQuotedArgumentDoesNotEndTheTag() {
        assertThat(Typography.apply("<hover:show_text:'a > b'>go", true)).isEqualTo("<hover:show_text:'a > b'>ɢᴏ");
    }

    @Test
    void aStrayBracketIsTextLikeMiniMessageTreatsIt() {
        assertThat(Typography.apply("5 < 6", true)).isEqualTo("5 < 6");
    }
}
