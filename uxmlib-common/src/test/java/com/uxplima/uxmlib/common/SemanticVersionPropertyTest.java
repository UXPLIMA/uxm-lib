package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based invariants for {@link SemanticVersion}. The worked examples in {@link SemanticVersionTest}
 * pin specific shapes; these assert the all-inputs requirements a fail-safe consumer depends on:
 * {@link SemanticVersion#tryParse} is total (never throws on any string), the ordering is a consistent total
 * order (reflexive, antisymmetric), and {@link SemanticVersion#isNewerThan} agrees with strict-greater
 * comparison. Versions are built through {@link SemanticVersion#parse} (the type has no public constructor).
 */
@org.jspecify.annotations.NullUnmarked
class SemanticVersionPropertyTest {

    /**
     * Requirement: {@code tryParse} is total: arbitrary text yields {@link java.util.Optional#empty()}
     * rather than throwing, so a fail-safe caller renders an unreadable version as "cannot compare" instead
     * of crashing a startup hook.
     */
    @Property
    void tryParse_is_total_for_arbitrary_text(@ForAll @StringLength(max = 40) String raw) {
        assertThatCode(() -> SemanticVersion.tryParse(raw)).doesNotThrowAnyException();
    }

    /** Requirement: a well-formed {@code major.minor.patch} string round-trips through parse + toString. */
    @Property
    void plain_triple_round_trips(
            @ForAll @IntRange(min = 0, max = 9_999) int major,
            @ForAll @IntRange(min = 0, max = 9_999) int minor,
            @ForAll @IntRange(min = 0, max = 9_999) int patch) {
        String raw = major + "." + minor + "." + patch;
        assertThat(SemanticVersion.parse(raw).toString()).isEqualTo(raw);
    }

    @Provide
    Arbitrary<SemanticVersion> versions() {
        Arbitrary<Integer> component = Arbitraries.integers().between(0, 5);
        Arbitrary<String> pre = Arbitraries.of("", "-alpha", "-beta", "-rc.1", "-SNAPSHOT");
        return Combinators.combine(component, component, component, pre)
                .as((major, minor, patch, tail) -> SemanticVersion.parse(major + "." + minor + "." + patch + tail));
    }

    /**
     * Requirement: two equal versions compare as equal and neither is newer: the ordering is consistent
     * with value equality. The second instance is parsed afresh from the first's canonical string.
     */
    @Property
    void equal_versions_compare_as_equal(@ForAll("versions") SemanticVersion v) {
        SemanticVersion same = SemanticVersion.parse(v.toString());
        assertThat(v.compareTo(same)).isZero();
        assertThat(v.isNewerThan(same)).isFalse();
    }

    /** Requirement: comparison is antisymmetric: swapping the operands flips the sign (a total order). */
    @Property
    void compareTo_is_antisymmetric(@ForAll("versions") SemanticVersion a, @ForAll("versions") SemanticVersion b) {
        assertThat(Integer.signum(a.compareTo(b))).isEqualTo(-Integer.signum(b.compareTo(a)));
    }

    /** Requirement: {@code isNewerThan} is exactly strict-greater: it must agree with {@code compareTo > 0}. */
    @Property
    void isNewerThan_agrees_with_strict_greater(
            @ForAll("versions") SemanticVersion a, @ForAll("versions") SemanticVersion b) {
        assertThat(a.isNewerThan(b)).isEqualTo(a.compareTo(b) > 0);
    }

    /**
     * Requirement (semver §11): a pre-release precedes its associated stable release: {@code 1.2.3-X} is
     * always lower than {@code 1.2.3}.
     */
    @Property
    void pre_release_is_lower_than_its_stable_release(
            @ForAll @IntRange(min = 0, max = 5) int major,
            @ForAll @IntRange(min = 0, max = 5) int minor,
            @ForAll @IntRange(min = 0, max = 5) int patch,
            @ForAll("nonEmptyPreRelease") String pre) {
        String triple = major + "." + minor + "." + patch;
        SemanticVersion stable = SemanticVersion.parse(triple);
        SemanticVersion preRelease = SemanticVersion.parse(triple + "-" + pre);
        assertThat(preRelease.compareTo(stable)).isNegative();
        assertThat(stable.isNewerThan(preRelease)).isTrue();
    }

    @Provide
    Arbitrary<String> nonEmptyPreRelease() {
        return Arbitraries.of("alpha", "beta", "rc.1", "SNAPSHOT", "M1");
    }
}
