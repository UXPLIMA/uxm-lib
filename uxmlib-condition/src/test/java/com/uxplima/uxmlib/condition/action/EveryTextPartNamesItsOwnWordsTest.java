package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A line may name the words it shows by their key in the catalogue, and every text part of a line may.
 *
 * <p>A key is written {@code @menu.state.title}. Every plugin used to look it up itself before the line reached
 * this engine, and every one of them took the whole rest of the line after the verb as one key. On 2026-09-23
 * a live player switching the lobby's visibility read {@code state.important.title} over
 * {@code @state.important.subtitle}: the line was {@code [subtitle] @state.important.title |
 * @state.important.subtitle 0.1 1.2 0.3}, which is one key per line and three times, and the lookup asked for
 * the whole of it as one key. uxmDailyRewards' reminder and uxmQuests' start and finish were written the same
 * way. A {@code [bossbar]} could not name a key at all, because its text comes after the seconds.
 *
 * <p>So the words are one seam of the context, and each text part asks it for its own key.
 */
class EveryTextPartNamesItsOwnWordsTest {

    private static final Map<String, String> CATALOGUE = Map.of(
            "state.title", "<green>Everybody",
            "state.subtitle", "You can see the whole lobby",
            "state.bar", "Everybody is visible",
            "state.chat", "You can see everybody again");

    @Test
    @DisplayName("a title and a subtitle each name their own key, with the times after them")
    void aTitleAndASubtitleEachNameTheirOwnKey() {
        Seen seen = new Seen();

        ActionList.parse(List.of("[subtitle] @state.title | @state.subtitle 0.1 1.2 0.3"))
                .run(context(seen));

        assertThat(seen.title).isEqualTo("Everybody");
        assertThat(seen.subtitle).isEqualTo("You can see the whole lobby");
    }

    @Test
    @DisplayName("a key and written words sit on either side of the pipe")
    void aKeyAndWrittenWordsMix() {
        Seen seen = new Seen();

        ActionList.parse(List.of("[subtitle] Welcome | @state.subtitle")).run(context(seen));

        assertThat(seen.title).isEqualTo("Welcome");
        assertThat(seen.subtitle).isEqualTo("You can see the whole lobby");
    }

    @Test
    @DisplayName("a message, an action bar and a title name a key the same way")
    void theOtherTextVerbsNameAKey() {
        Seen seen = new Seen();

        ActionList.parse(List.of("[message] @state.chat", "[actionbar] @state.bar", "[title] @state.title"))
                .run(context(seen));

        assertThat(seen.messages).containsExactly("You can see everybody again");
        assertThat(seen.bar).isEqualTo("Everybody is visible");
        assertThat(seen.title).isEqualTo("Everybody");
    }

    @Test
    @DisplayName("a boss bar names its key after the seconds and the colour")
    void aBossBarNamesItsKey() {
        Player player = mock(Player.class);
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .target(player)
                .later((delay, what) -> {})
                .words(CATALOGUE::get)
                .build();

        ActionList.parse(List.of("[bossbar] 5 RED PROGRESS | @state.bar")).run(context);

        ArgumentCaptor<BossBar> shown = ArgumentCaptor.forClass(BossBar.class);
        verify(player).showBossBar(shown.capture());
        assertThat(Text.plain(shown.getValue().name())).isEqualTo("Everybody is visible");
    }

    @Test
    @DisplayName("words written out are left as they are, an at sign inside them included")
    void writtenWordsAreLeftAlone() {
        Seen seen = new Seen();

        ActionList.parse(List.of("[message] mail me @ the forum")).run(context(seen));

        assertThat(seen.messages).containsExactly("mail me @ the forum");
    }

    @Test
    @DisplayName("a key with no words wired says which method to call, rather than drawing the key")
    void anUnwiredKeySaysSo() {
        Seen seen = new Seen();
        ActionContext unwired =
                ActionContext.builder(OperandResolver.identity()).target(seen).build();

        assertThatThrownBy(
                        () -> ActionList.parse(List.of("[message] @state.chat")).run(unwired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("words");
        assertThat(seen.messages).isEmpty();
    }

    private static ActionContext context(Seen seen) {
        return ActionContext.builder(OperandResolver.identity())
                .target(seen)
                .words(CATALOGUE::get)
                .build();
    }

    /** An audience that keeps the text it was shown, as a player would read it. */
    private static final class Seen implements Audience {

        private final List<String> messages = new ArrayList<>();
        private String title = "";
        private String subtitle = "";
        private String bar = "";

        @Override
        public void sendMessage(Component message) {
            messages.add(Text.plain(message));
        }

        @Override
        public void sendActionBar(Component message) {
            bar = Text.plain(message);
        }

        @Override
        public void showTitle(Title shown) {
            title = Text.plain(shown.title());
            subtitle = Text.plain(shown.subtitle());
        }

        @Override
        public <T> void sendTitlePart(TitlePart<T> part, T value) {}
    }
}
