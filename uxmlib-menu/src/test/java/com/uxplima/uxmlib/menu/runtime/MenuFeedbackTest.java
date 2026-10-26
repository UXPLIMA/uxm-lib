package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.function.Predicate;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.style.MenuSounds;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.Ref;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

/**
 * When the engine makes a noise. Which noise is the host's file to decide, so every assertion here is about the
 * decision the engine does own: whether a gesture gets a sound at all.
 *
 * <p>Three sounds that cannot be confused with each other stand in for the host's tones, because the interesting
 * cases are the ones where the wrong sound would still be a sound. A test asserting only that something played would
 * pass with a click tone on a refusal.
 */
class MenuFeedbackTest {

    private static final Key CLICK = Key.key("test", "click");

    private static final Key PAGE = Key.key("test", "page");

    private static final Key DENIED = Key.key("test", "denied");

    private static final List<Ref> SOME_ACTIONS = List.of(Ref.parse("give:diamond"));

    /** The host says every gesture speaks for itself, the opposite of the shipped answer. */
    private static boolean suppressesEverything(List<Ref> actions) {
        return true;
    }

    private PlayerMock viewer;

    private MenuHolder holder;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        holder = new MenuHolder("menu", new MenuSpecLoader().parse("rows = 3"), MenuContext.of(viewer, null, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuSounds sounds() {
        return new MenuSounds(
                Sound.sound(Key.key("test", "open"), Sound.Source.MASTER, 1f, 1f),
                Sound.sound(CLICK, Sound.Source.MASTER, 1f, 1f),
                Sound.sound(PAGE, Sound.Source.MASTER, 1f, 1f),
                Sound.sound(DENIED, Sound.Source.MASTER, 1f, 1f));
    }

    private MenuFeedback feedback(Predicate<List<Ref>> speaksForItself) {
        return new MenuFeedback(MenuFeedbackTest::sounds, speaksForItself);
    }

    private List<String> heard() {
        return viewer.getHeardSounds().stream().map(AudioExperience::getSound).toList();
    }

    // -- an ordinary click ---------------------------------------------------------------------------------

    @Test
    void aGestureThatRanSomethingGetsTheClickTone() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).click(holder, SOME_ACTIONS);

        assertThat(heard()).containsExactly(CLICK.asString());
    }

    /** A slot with nothing bound to it did nothing, and a sound would tell the viewer it did something. */
    @Test
    void aGestureThatRanNothingIsSilent() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).click(holder, List.of());

        assertThat(heard()).isEmpty();
    }

    @Test
    void aGestureTheHostSaysSpeaksForItselfIsNotSpokenOver() {
        feedback(MenuFeedbackTest::suppressesEverything).click(holder, SOME_ACTIONS);

        assertThat(heard()).isEmpty();
    }

    // -- a page turn ---------------------------------------------------------------------------------------

    @Test
    void aPageButtonGetsThePageToneAndNotTheClickOne() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).page(holder, SOME_ACTIONS);

        assertThat(heard()).containsExactly(PAGE.asString());
    }

    /**
     * A page button with no actions of its own still turned the page, so unlike an ordinary click it is not silent.
     * The two methods differ on exactly this and nothing else, which is why it is asserted rather than assumed.
     */
    @Test
    void aPageButtonWithNoActionsStillSoundsBecauseThePageStillTurned() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).page(holder, List.of());

        assertThat(heard()).containsExactly(PAGE.asString());
    }

    @Test
    void aPageButtonCarryingItsOwnSoundIsNotSpokenOver() {
        feedback(MenuFeedbackTest::suppressesEverything).page(holder, SOME_ACTIONS);

        assertThat(heard()).isEmpty();
    }

    // -- a refusal -----------------------------------------------------------------------------------------

    @Test
    void aDeniedGestureGetsTheRefusalTone() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).deny(holder);

        assertThat(heard()).containsExactly(DENIED.asString());
    }

    /**
     * Nothing ran, so nothing could have spoken for itself. A refusal is the one tone the host cannot suppress, and a
     * silent refusal is indistinguishable from a broken button.
     */
    @Test
    void aRefusalSoundsEvenWhenTheHostSuppressesEverythingElse() {
        feedback(MenuFeedbackTest::suppressesEverything).deny(holder);

        assertThat(heard()).containsExactly(DENIED.asString());
    }

    // -- the shipped answer, and a viewer who left -----------------------------------------------------------

    /** A host that wants every gesture spoken over, its own sounds included, still has the answer that says so. */
    @Test
    void theAnswerThatSuppressesNothingSuppressesNothing() {
        assertThat(MenuFeedback.SUPPRESSES_NOTHING.test(SOME_ACTIONS)).isFalse();
        assertThat(MenuFeedback.SUPPRESSES_NOTHING.test(List.of())).isFalse();
    }

    /**
     * The shipped answer: a gesture that runs the engine's own {@code sound} verb speaks for itself.
     *
     * <p>The shipped answer used to be that nothing does, so a button written {@code ["open:x", "sound:..."]} played
     * the click tone and then its own sound, one tick apart, and every menu of every host that wrote a sound on a
     * button played two. No host ever wired the one line that would have fixed it. The verb is the engine's own, not a
     * host's, so knowing it holds no copy of anybody's vocabulary.
     */
    @Test
    void theShippedAnswerLetsAButtonWithItsOwnSoundSpeak() {
        List<Ref> withSound = List.of(Ref.parse("open:shelf"), Ref.parse("sound:block.note_block.pling 0.6 1.5"));

        feedback(MenuFeedback.SHIPPED).click(holder, withSound);

        assertThat(heard()).isEmpty();
    }

    /** A button with no sound of its own still answers with the click tone, so it does not read as broken. */
    @Test
    void theShippedAnswerStillClicksForAButtonWithoutASound() {
        feedback(MenuFeedback.SHIPPED).click(holder, SOME_ACTIONS);

        assertThat(heard()).containsExactly(CLICK.asString());
    }

    /** A page button that writes its own sound gets that sound alone, the same rule as a click. */
    @Test
    void theShippedAnswerLetsAPageButtonWithItsOwnSoundSpeak() {
        feedback(MenuFeedback.SHIPPED).page(holder, List.of(Ref.parse("sound:item.book.page_turn 0.7 1")));

        assertThat(heard()).isEmpty();
    }

    // -- a host that gave no sounds ------------------------------------------------------------------------------

    /**
     * A listener the host gave no sounds plays none: not a click, not a page turn, not a refusal.
     *
     * <p>The engine used to fall back on tones of its own, so every menu of every host made the library's sounds,
     * and no operator could change or silence them because no host had wired a file to them. A sound is a look, and a
     * look is the host's to choose.
     */
    @Test
    void aListenerGivenNoSoundsIsSilent() {
        MenuFeedback silent = MenuFeedback.SILENT;

        silent.click(holder, SOME_ACTIONS);
        silent.page(holder, SOME_ACTIONS);
        silent.deny(holder);

        assertThat(heard()).isEmpty();
    }

    /** The sounds are asked for at each gesture, so a reload that changes the file changes the next click. */
    @Test
    void theSoundsAreReadAtEachGesture() {
        Key[] click = {CLICK};
        MenuFeedback live = new MenuFeedback(
                () -> new MenuSounds(
                        Sound.sound(Key.key("test", "open"), Sound.Source.MASTER, 1f, 1f),
                        Sound.sound(click[0], Sound.Source.MASTER, 1f, 1f),
                        Sound.sound(PAGE, Sound.Source.MASTER, 1f, 1f),
                        Sound.sound(DENIED, Sound.Source.MASTER, 1f, 1f)),
                MenuFeedback.SHIPPED);

        live.click(holder, SOME_ACTIONS);
        click[0] = Key.key("test", "reloaded");
        live.click(holder, SOME_ACTIONS);

        assertThat(heard()).containsExactly(CLICK.asString(), "test:reloaded");
    }

    /** A player who logged off between the click and the sound is skipped, not thrown into the click dispatch. */
    @Test
    void aViewerWhoLeftBetweenTheClickAndTheSoundIsSkippedQuietly() {
        viewer.disconnect();

        assertThatCode(() -> feedback(MenuFeedback.SUPPRESSES_NOTHING).click(holder, SOME_ACTIONS))
                .doesNotThrowAnyException();
        assertThat(heard()).isEmpty();
    }
}
