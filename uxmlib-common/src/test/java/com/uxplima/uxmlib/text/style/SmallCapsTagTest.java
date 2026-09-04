package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The tag that converts a value, which is the one thing the letters pass never sees. */
class SmallCapsTagTest {

    private static String plain(
            String template, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(Text.mini(template, resolvers));
    }

    @Test
    @DisplayName("the value a placeholder carries is written in small capitals")
    void avalueIsConverted() {
        assertThat(plain("<caps><item></caps>", Text.placeholder("item", "Diamond Sword")))
                .isEqualTo("ᴅɪᴀᴍᴏɴᴅ ꜱᴡᴏʀᴅ");
    }

    @Test
    @DisplayName("what is outside the tag is left alone")
    void onlyWhatIsHeldIsConverted() {
        assertThat(plain(
                        "Sold by <caps><who></caps> to <buyer>",
                        Text.placeholder("who", "Ada"),
                        Text.placeholder("buyer", "Bora")))
                .isEqualTo("Sold by ᴀᴅᴀ to Bora");
    }

    @Test
    @DisplayName("a number and a punctuation mark are readable, so they come back as they are")
    void adigitIsNotALetter() {
        assertThat(plain("<caps><value></caps>", Text.placeholder("value", "1,250 (x3)")))
                .isEqualTo("1,250 (x3)");
    }

    @Test
    @DisplayName("a colour inside the tag is still a colour")
    void thestyleSurvives() {
        Component drawn = Text.mini("<caps><green>on the shelf</green></caps>");

        assertThat(PlainTextComponentSerializer.plainText().serialize(drawn)).isEqualTo("ᴏɴ ᴛʜᴇ ꜱʜᴇʟꜰ");
        assertThat(MiniMessage.miniMessage().serialize(drawn)).contains("green");
    }

    @Test
    @DisplayName("a component that holds no letters of its own keeps everything it is")
    void anotherKindOfComponentIsNotLost() {
        Component drawn = Text.mini(
                "<caps><thing></caps>",
                Text.component("thing", Component.translatable("item.minecraft.diamond_sword")));

        assertThat(drawn.toString()).contains("item.minecraft.diamond_sword");
    }
}
