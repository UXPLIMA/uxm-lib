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
    void thecapsMarkersAreKeptForALanguageThatIsConverted() {
        assertThat(Typography.apply("Sold by <caps><who></caps>", true)).isEqualTo("ꜱᴏʟᴅ ʙʏ <caps><who></caps>");
    }

    @Test
    void thecapsMarkersGoAwayForALanguageThatKeepsItsLetters() {
        assertThat(Typography.apply("Sold by <caps><who></caps>", false)).isEqualTo("Sold by <who>");
    }

    @Test
    void thecapsMarkersGoAwayInsideThePlainMarkers() {
        assertThat(Typography.apply("id <plain><caps><id></caps></plain>", true))
                .isEqualTo("ɪᴅ <id>");
    }

    @Test
    void aBracketInsideAQuotedArgumentDoesNotEndTheTag() {
        assertThat(Typography.apply("<click:suggest_command:'a > b'>go", true))
                .isEqualTo("<click:suggest_command:'a > b'>ɢᴏ");
    }

    @Test
    void aStrayBracketIsTextLikeMiniMessageTreatsIt() {
        assertThat(Typography.apply("5 < 6", true)).isEqualTo("5 < 6");
    }

    /**
     * A line that names a catalogue entry is a reference, not words: its entry is converted where the catalogue
     * is, and the reference itself must reach the lookup as written or it names nothing.
     */
    @Test
    void aLineNamingACatalogueEntryIsLeftAsWritten() {
        assertThat(Typography.apply("@item.selector.title", true)).isEqualTo("@item.selector.title");
        assertThat(Typography.apply("@shop.price item=sword note='two words'", true))
                .isEqualTo("@shop.price item=sword note='two words'");
    }

    /** An effect line may name a key on each side of a pipe; each key keeps its letters and the rest converts. */
    @Test
    void aKeyInsideALineKeepsItsLetters() {
        assertThat(Typography.apply("@state.title | @state.subtitle 0.1 1.2 0.3", true))
                .isEqualTo("@state.title | @state.subtitle 0.1 1.2 0.3");
        assertThat(Typography.apply("hi @everyone", true)).isEqualTo("ʜɪ @everyone");
    }

    /**
     * The text a hover shows is read by the player, so its letters follow the line's; the rest of the tag, and every
     * tag and placeholder inside the hover, is copied as written.
     */
    @Test
    void theTextAHoverShowsFollowsTheLine() {
        assertThat(Typography.apply("<hover:show_text:'<muted>click to copy {uri}</muted>'>{uri}</hover>", true))
                .isEqualTo("<hover:show_text:'<muted>ᴄʟɪᴄᴋ ᴛᴏ ᴄᴏᴘʏ {uri}</muted>'>{uri}</hover>");
        assertThat(Typography.apply("<hover:show_text:'click'>go", false)).isEqualTo("<hover:show_text:'click'>go");
    }

    /**
     * A {@code {name}} placeholder is filled by text substitution after this pass, so it keeps its spelling: in small
     * capitals it named nothing, and the player read the braces.
     */
    @Test
    void aBracePlaceholderKeepsItsSpelling() {
        assertThat(Typography.apply("Hello {player}, you have {count} homes", true))
                .isEqualTo("ʜᴇʟʟᴏ {player}, ʏᴏᴜ ʜᴀᴠᴇ {count} ʜᴏᴍᴇꜱ");
    }

    /** A PlaceholderAPI token is read by another plugin after this pass, so it keeps its spelling too. */
    @Test
    void aPlaceholderApiTokenKeepsItsSpelling() {
        assertThat(Typography.apply("Online %server_online% now", true)).isEqualTo("ᴏɴʟɪɴᴇ %server_online% ɴᴏᴡ");
        assertThat(Typography.apply("Players %bungee_{server}%", true)).isEqualTo("ᴘʟᴀʏᴇʀꜱ %bungee_{server}%");
    }

    /** A per cent sign in prose is followed by a space and opens no placeholder, so the words after it convert. */
    @Test
    void aPerCentSignInProseIsText() {
        assertThat(Typography.apply("50% off today", true)).isEqualTo("50% ᴏꜰꜰ ᴛᴏᴅᴀʏ");
    }
}
