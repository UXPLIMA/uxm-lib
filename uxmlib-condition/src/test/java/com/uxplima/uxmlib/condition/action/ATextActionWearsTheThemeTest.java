package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The text of a text action wears the theme: its roles and labels are painted, not shown as markup.
 *
 * <p>uxm-plots found its vote thanks reading {@code <tag:'PLOTS'><body>Thank you for looking.}: an effect line went
 * to the parser as written and was drawn with plain MiniMessage, which has never heard of the theme's tags. 94 lines
 * our plugins ship carried the same tags. Styling the whole line before parsing was no answer either: the prefix
 * {@code [message]} became {@code [ᴍᴇꜱꜱᴀɢᴇ]}, which the parser rejects.
 */
class ATextActionWearsTheThemeTest {

    @Test
    @DisplayName("an unwired context paints the roles in the default theme")
    void anUnwiredContextPaintsTheDefaultTheme() {
        Seen seen = new Seen();

        ActionList.parse(List.of("[message] <tag:'JOBS'> <body>The pickaxe is yours."))
                .run(ActionContext.builder(OperandResolver.identity())
                        .target(seen)
                        .build());

        assertThat(seen.messages)
                .singleElement()
                .asString()
                .doesNotContain("<tag:", "<body>", "</")
                .contains("JOBS", "The pickaxe is yours.");
    }

    @Test
    @DisplayName("a plugin's own style reaches every text part, and the verb and the tags in front are left to parse")
    void aPluginsStyleReachesEveryTextPart() {
        Seen seen = new Seen();
        Styler styler = new Styler(Theme.defaults());
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .target(seen)
                .style(line -> styler.tokens(line.replace("l", "1").replace("t", "7"), Locale.ENGLISH))
                .build();

        ActionList.parse(List.of("[message] <body>hello", "[subtitle] <body>top | <body>bottom"))
                .run(context);

        assertThat(seen.messages).containsExactly("he11o");
        assertThat(seen.title).isEqualTo("7op");
        assertThat(seen.subtitle).isEqualTo("bo77om");
    }

    /** An audience that keeps the text it was shown, as a player would read it. */
    private static final class Seen implements Audience {

        private final List<String> messages = new ArrayList<>();
        private String title = "";
        private String subtitle = "";

        @Override
        public void sendMessage(Component message) {
            messages.add(Text.plain(message));
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
