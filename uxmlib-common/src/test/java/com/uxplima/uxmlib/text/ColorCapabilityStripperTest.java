package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ColorCapabilityStripperTest {

    private static final Set<ColorCapability> NONE = Set.of();
    private static final Set<ColorCapability> ALL = EnumSet.allOf(ColorCapability.class);

    @Test
    void all_capabilities_allowed_returns_text_verbatim() {
        String text = "<red>hi</red> <gradient:red:blue>world</gradient> <#ff8800>!</#ff8800>";
        assertThat(ColorCapabilityStripper.strip(text, ALL)).isEqualTo(text);
    }

    @Test
    void no_capabilities_strips_every_styling_tag() {
        String text = "<red>hello</red> <bold>shouty</bold> <#ff8800>orange</#ff8800>";
        assertThat(ColorCapabilityStripper.strip(text, NONE)).isEqualTo("hello shouty orange");
    }

    @Test
    void basic_colour_kept_when_basic_allowed() {
        assertThat(ColorCapabilityStripper.strip("<red>hi</red>", EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("<red>hi</red>");
    }

    @Test
    void basic_colour_stripped_when_basic_disallowed() {
        assertThat(ColorCapabilityStripper.strip("<red>hi</red>", EnumSet.of(ColorCapability.HEX)))
                .isEqualTo("hi");
    }

    @Test
    void hex_short_form_categorised_as_hex() {
        assertThat(ColorCapabilityStripper.strip("<#ff8800>orange</#ff8800>", EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("orange");
        assertThat(ColorCapabilityStripper.strip("<#ff8800>orange</#ff8800>", EnumSet.of(ColorCapability.HEX)))
                .isEqualTo("<#ff8800>orange</#ff8800>");
    }

    @Test
    void color_long_form_with_hex_arg_is_hex_category() {
        // <color:#xx> falls under HEX even though the head is `color`.
        assertThat(ColorCapabilityStripper.strip("<color:#ff8800>x</color>", EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("x");
    }

    @Test
    void color_long_form_with_named_arg_is_basic_category() {
        assertThat(ColorCapabilityStripper.strip("<color:red>x</color>", EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("<color:red>x</color>");
    }

    @Test
    void gradient_stripped_when_gradient_disallowed() {
        assertThat(ColorCapabilityStripper.strip(
                        "<gradient:red:blue>fancy</gradient>", EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("fancy");
    }

    @Test
    void rainbow_stripped_when_rainbow_disallowed() {
        assertThat(ColorCapabilityStripper.strip("<rainbow>!!!</rainbow>", EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("!!!");
    }

    @Test
    void formatting_tags_stripped_when_formatting_disallowed() {
        String text = "<bold>BIG</bold> <i>note</i> <u>under</u>";
        assertThat(ColorCapabilityStripper.strip(text, EnumSet.of(ColorCapability.BASIC)))
                .isEqualTo("BIG note under");
    }

    @Test
    void mixed_input_strips_only_disallowed_classes() {
        String text = "<red>basic</red> <#ff8800>hex</#ff8800> <bold>fmt</bold>";
        // Allow BASIC + FORMATTING but not HEX.
        Set<ColorCapability> allowed = EnumSet.of(ColorCapability.BASIC, ColorCapability.FORMATTING);
        assertThat(ColorCapabilityStripper.strip(text, allowed)).isEqualTo("<red>basic</red> hex <bold>fmt</bold>");
    }

    @Test
    void unknown_tag_left_alone() {
        // Click events / placeholders / reset are non-styling: leave untouched
        // so operators using <click:open_url:...> in admin formats don't get
        // their tags stripped just because the player has no colour rights.
        assertThat(ColorCapabilityStripper.strip("<reset>x<click:run_command:/foo>y</click>", NONE))
                .isEqualTo("<reset>x<click:run_command:/foo>y</click>");
    }

    @Test
    void plain_text_passes_through_unchanged() {
        assertThat(ColorCapabilityStripper.strip("just words", NONE)).isEqualTo("just words");
    }

    @Test
    void empty_text_returns_empty() {
        assertThat(ColorCapabilityStripper.strip("", NONE)).isEqualTo("");
    }

    // P16 #24: extracted leading-colour predicate shared with ChatColorApplier.
    @Test
    void starts_with_colour_tag_true_for_named_hex_gradient_rainbow() {
        assertThat(ColorCapabilityStripper.startsWithColourTag("<red>hi")).isTrue();
        assertThat(ColorCapabilityStripper.startsWithColourTag("<#ff8800>hi")).isTrue();
        assertThat(ColorCapabilityStripper.startsWithColourTag("<gradient:red:blue>hi"))
                .isTrue();
        assertThat(ColorCapabilityStripper.startsWithColourTag("<rainbow>hi")).isTrue();
        assertThat(ColorCapabilityStripper.startsWithColourTag("<color:red>hi")).isTrue();
    }

    @Test
    void starts_with_colour_tag_false_for_plain_or_formatting_only() {
        assertThat(ColorCapabilityStripper.startsWithColourTag("hello")).isFalse();
        assertThat(ColorCapabilityStripper.startsWithColourTag("<bold>hi")).isFalse();
        assertThat(ColorCapabilityStripper.startsWithColourTag("")).isFalse();
        assertThat(ColorCapabilityStripper.startsWithColourTag("  <red>leading space"))
                .isFalse();
    }
}
