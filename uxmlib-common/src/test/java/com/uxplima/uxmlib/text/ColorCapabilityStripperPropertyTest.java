package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based invariants for {@link ColorCapabilityStripper}. These assert
 * requirements that must hold for <em>every</em> input, complementing the
 * worked examples in {@link ColorCapabilityStripperTest}.
 *
 * <p>Inputs are assembled from a small vocabulary that mixes literal text with
 * styling tags of every {@link ColorCapability} class plus structural /
 * placeholder tags the stripper must leave alone, so the generated space
 * actually exercises the categorise-and-drop logic rather than only plain text.
 */
@org.jspecify.annotations.NullUnmarked
class ColorCapabilityStripperPropertyTest {

    private static final Set<ColorCapability> ALL = EnumSet.allOf(ColorCapability.class);

    /** Tokens the generator interleaves: styling tags, structural tags, and plain words. */
    private static final String[] VOCAB = {
        "<red>",
        "</red>",
        "<blue>",
        "<gold>",
        "<color:red>",
        "<color:#ff8800>",
        "</color>",
        "<#ff8800>",
        "</#ff8800>",
        "<gradient:red:blue>",
        "</gradient>",
        "<rainbow>",
        "</rainbow>",
        "<bold>",
        "</bold>",
        "<i>",
        "</i>",
        "<u>",
        "<reset>",
        "<newline>",
        "<click:run_command:/foo>",
        "</click>",
        "<unknown_tag>",
        "<player_name>",
        "hello",
        " ",
        "world",
        "!",
        "[item]",
        "abc"
    };

    @Provide
    Arbitrary<String> chatBodies() {
        Arbitrary<String> piece = Arbitraries.of(VOCAB);
        return piece.list().ofMaxSize(12).map(parts -> String.join("", parts));
    }

    @Provide
    Arbitrary<Set<ColorCapability>> capabilitySets() {
        // Set.copyOf (not EnumSet.copyOf) so the empty subset is representable.
        return Arbitraries.subsetOf(ALL.toArray(ColorCapability[]::new)).map(Set::copyOf);
    }

    /**
     * Requirement: stripping is idempotent. A second pass with the same allowed
     * set finds nothing more to remove: the visible/permitted form is stable.
     */
    @Property
    void stripping_is_idempotent(@ForAll("chatBodies") String body, @ForAll("capabilitySets") Set<ColorCapability> ok) {
        String once = ColorCapabilityStripper.strip(body, ok);
        String twice = ColorCapabilityStripper.strip(once, ok);
        assertThat(twice).isEqualTo(once);
    }

    /**
     * Requirement: when every capability is allowed the body is returned
     * verbatim: the gate is a no-op for a fully-trusted sender.
     */
    @Property
    void all_capabilities_allowed_is_identity(@ForAll("chatBodies") String body) {
        assertThat(ColorCapabilityStripper.strip(body, ALL)).isEqualTo(body);
    }

    /**
     * Requirement: stripping only ever removes characters, never adds them, so
     * the output can never be longer than the input (no escaping/expansion).
     */
    @Property
    void output_is_never_longer_than_input(
            @ForAll("chatBodies") String body, @ForAll("capabilitySets") Set<ColorCapability> ok) {
        assertThat(ColorCapabilityStripper.strip(body, ok).length()).isLessThanOrEqualTo(body.length());
    }

    /**
     * Requirement: a more permissive allowed set never strips <em>more</em>
     * than a subset of it. Granting an extra capability can only preserve more
     * tags, never remove additional ones, so the superset output is at least as
     * long as the subset output (monotonicity of permissiveness).
     */
    @Property
    void more_permissive_set_keeps_at_least_as_much(@ForAll("chatBodies") String body) {
        String none = ColorCapabilityStripper.strip(body, Set.of());
        String basicOnly = ColorCapabilityStripper.strip(body, EnumSet.of(ColorCapability.BASIC));
        String all = ColorCapabilityStripper.strip(body, ALL);
        assertThat(none.length()).isLessThanOrEqualTo(basicOnly.length());
        assertThat(basicOnly.length()).isLessThanOrEqualTo(all.length());
    }

    /**
     * Requirement: plain text containing no {@code <} can never be altered,
     * regardless of which capabilities are allowed: there are no tags to gate.
     */
    @Property
    void plain_text_without_angle_brackets_is_untouched(
            @ForAll @StringLength(max = 40) String raw, @ForAll("capabilitySets") Set<ColorCapability> ok) {
        String plain = raw.replace('<', 'x').replace('>', 'x');
        assertThat(ColorCapabilityStripper.strip(plain, ok)).isEqualTo(plain);
    }

    /**
     * Requirement (the "explicit colour wins" predicate's contract): if a body
     * opens with an explicit colour tag, stripping <em>all</em> styles must
     * remove that opener, so the stripped form no longer opens with a colour
     * tag. {@code startsWithColourTag} and {@code stripAllStyles} share one
     * categorisation source, so they must agree.
     */
    @Property
    void stripping_all_styles_clears_a_leading_colour_tag(@ForAll("chatBodies") String body) {
        if (ColorCapabilityStripper.startsWithColourTag(body)) {
            String bare = ColorCapabilityStripper.stripAllStyles(body);
            assertThat(ColorCapabilityStripper.startsWithColourTag(bare)).isFalse();
        }
    }
}
