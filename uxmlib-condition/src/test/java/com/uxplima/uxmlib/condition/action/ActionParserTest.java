package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The parser is pure: a config string maps to the right {@link ActionType}, the payload after the prefix, and
 * a closure. These tests assert the routing and the documented error cases without running any side effect.
 */
class ActionParserTest {

    @Test
    void messageRoutesToMessageTypeAndKeepsThePayloadVerbatim() {
        ParsedAction parsed = ActionParser.parse("[message] <green>Hello, %player_name%!");
        assertThat(parsed.type()).isEqualTo(ActionType.MESSAGE);
        assertThat(parsed.payload()).isEqualTo("<green>Hello, %player_name%!");
    }

    /**
     * The three verbs an interaction needed and did not have.
     *
     * <p>{@code [title]} sends an empty subtitle and the vanilla timings, so a refusal that wants a second
     * line under it, or a win that wants to stay on screen, could not be written. There was no bar and no
     * visual at all. The owner set the standard on 2026-09-08: every interaction fires as many effects as the
     * operator writes, in the order they wrote them, and that is not a question to be asked per plugin.
     */
    @Test
    void theThreeNewVerbsParse() {
        assertThat(ActionParser.parse("[subtitle] Not enough keys | You need one more")
                        .type())
                .isEqualTo(ActionType.SUBTITLE);
        assertThat(ActionParser.parse("[bossbar] 5 RED PROGRESS | Opening").type())
                .isEqualTo(ActionType.BOSSBAR);
        assertThat(ActionParser.parse("[particle] HAPPY_VILLAGER 20 0.5").type())
                .isEqualTo(ActionType.PARTICLE);
    }

    @Test
    void aSubtitleSplitsOnThePipeAndReadsItsThreeTimes() {
        ParsedAction parsed = ActionParser.parse("[subtitle] Won | A diamond 1 4 1");

        assertThat(parsed.payload()).isEqualTo("Won | A diamond 1 4 1");
        assertThat(parsed.action()).isNotNull();
    }

    /** A subtitle with no times keeps the vanilla ones rather than refusing the line. */
    @Test
    void aSubtitleWithoutTimesStillParses() {
        assertThat(ActionParser.parse("[subtitle] Won | A diamond").type()).isEqualTo(ActionType.SUBTITLE);
    }

    /** A bar has to say how long it stays, because nothing else takes it down. */
    @Test
    void aBossBarWithoutItsTextIsRefused() {
        assertThatThrownBy(() -> ActionParser.parse("[bossbar] 5 RED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<seconds>");
    }

    /** A colour or an overlay the server does not know falls back rather than refusing the whole line. */
    @Test
    void anUnknownBossBarColourFallsBack() {
        assertThat(ActionParser.parse("[bossbar] 3 chartreuse | Hi").type()).isEqualTo(ActionType.BOSSBAR);
    }

    /** A particle with no count and no spread takes the defaults, so the short form is the usual one. */
    @Test
    void aParticleNeedsOnlyItsName() {
        assertThat(ActionParser.parse("[particle] FLAME").type()).isEqualTo(ActionType.PARTICLE);
    }

    @Test
    void prefixMatchingIsCaseInsensitive() {
        assertThat(ActionParser.parse("[BROADCAST] hi").type()).isEqualTo(ActionType.BROADCAST);
        assertThat(ActionParser.parse("[Console] say hi").type()).isEqualTo(ActionType.CONSOLE);
    }

    @Test
    void everyKnownPrefixParses() {
        assertThat(ActionParser.parse("[player] spawn").type()).isEqualTo(ActionType.PLAYER);
        assertThat(ActionParser.parse("[actionbar] hi").type()).isEqualTo(ActionType.ACTIONBAR);
        assertThat(ActionParser.parse("[title] hi").type()).isEqualTo(ActionType.TITLE);
        assertThat(ActionParser.parse("[sound] minecraft:ui.button.click").type())
                .isEqualTo(ActionType.SOUND);
    }

    @Test
    void closeNeedsNoPayload() {
        ParsedAction parsed = ActionParser.parse("[close]");
        assertThat(parsed.type()).isEqualTo(ActionType.CLOSE);
        assertThat(parsed.payload()).isEmpty();
    }

    @Test
    void leadingWhitespaceBeforeThePrefixIsTolerated() {
        assertThat(ActionParser.parse("   [message] hi").type()).isEqualTo(ActionType.MESSAGE);
    }

    @Test
    void unknownPrefixIsRejectedWithTheName() {
        assertThatThrownBy(() -> ActionParser.parse("[teleport] world"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teleport");
    }

    @Test
    void aStringWithoutABracketPrefixIsRejected() {
        assertThatThrownBy(() -> ActionParser.parse("message hi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[prefix]");
    }

    @Test
    void aMissingClosingBracketIsRejected() {
        assertThatThrownBy(() -> ActionParser.parse("[message hi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closing");
    }

    @Test
    void aPayloadRequiringTypeWithoutAPayloadIsRejected() {
        assertThatThrownBy(() -> ActionParser.parse("[message]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a payload");
    }

    @Test
    void aNonNumericSoundVolumeIsRejectedAtParseTime() {
        assertThatThrownBy(() -> ActionParser.parse("[sound] minecraft:ui.button.click loud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("volume");
    }

    @Test
    void textActionsAreFlaggedAsyncWhileCommandActionsAreSync() {
        assertThat(ActionParser.parse("[message] hi").action().async()).isTrue();
        assertThat(ActionParser.parse("[actionbar] hi").action().async()).isTrue();
        assertThat(ActionParser.parse("[console] say hi").action().async()).isFalse();
        assertThat(ActionParser.parse("[player] spawn").action().async()).isFalse();
        assertThat(ActionParser.parse("[close]").action().async()).isFalse();
    }
}
