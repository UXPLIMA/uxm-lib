package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every id a menu file names, read back and asked whether anybody answers it.
 *
 * <p>A menu is a file an operator edits and an id is a word they type. The engine looks each one up when
 * it needs it, and until now it found nothing and said nothing: an action id nobody registered was a
 * button that did nothing, and a condition id nobody registered hid the tile it guarded, which is the
 * safe direction and the quieter one. A hidden tile produces no click, so the click-time warning can
 * never reach it.
 *
 * <p>So the file is read once, when the menu is first opened and everything is wired, and the ids it
 * names are checked in one pass. That is the only moment both halves exist: at registration a plugin may
 * not have registered its vocabulary yet, and at render a warning would repeat several times a second.
 *
 * <p>The resolution rule is the engine's own, so a namespaced id that carries a value still counts as
 * registered: {@code auction:sort:newest} finds {@code auction:sort} and is not reported.
 */
class SpecRefsTest {

    @Test
    @DisplayName("an action nobody registered is named, and a registered one is not")
    void unknownActionsAreNamed() {
        MenuSpec spec = specWith(List.of(new Ref("glow:set", Map.of()), new Ref("glow:st", Map.of())), List.of());

        assertThat(SpecRefs.unknownActions(spec, Set.of("glow:set")::contains)).containsExactly("glow:st");
    }

    @Test
    @DisplayName("a namespaced id that carries a value counts as registered")
    void anamespacedIdResolves() {
        MenuSpec spec = specWith(List.of(new Ref("auction:sort:newest", Map.of())), List.of());

        assertThat(SpecRefs.unknownActions(spec, Set.of("auction:sort")::contains))
                .isEmpty();
    }

    @Test
    @DisplayName("a condition nobody registered is named, which no click can ever report")
    void unknownConditionsAreNamed() {
        MenuSpec spec = specWith(List.of(), List.of(new Ref("shop:can-afford", Map.of())));

        assertThat(SpecRefs.unknownConditions(spec, id -> false)).containsExactly("shop:can-afford");
    }

    @Test
    @DisplayName("the ids of every item are read, not only the first")
    void everyitemIsRead() {
        MenuItemSpec one = item(List.of(new Ref("a", Map.of())), List.of());
        MenuItemSpec two = item(List.of(new Ref("b", Map.of())), List.of());
        MenuSpec spec = new MenuSpec(
                "t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of("one", one, "two", two));

        assertThat(SpecRefs.unknownActions(spec, id -> false)).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("the same unknown id twice is reported once")
    void duplicatesAreReportedOnce() {
        MenuSpec spec = specWith(List.of(new Ref("x", Map.of()), new Ref("x", Map.of())), List.of());

        assertThat(SpecRefs.unknownActions(spec, id -> false)).containsExactly("x");
    }

    private static MenuSpec specWith(List<Ref> actions, List<Ref> conditions) {
        return new MenuSpec(
                "t",
                1,
                new RefreshSpec(false, 0),
                List.of(),
                List.of(),
                List.of(),
                Map.of("only", item(actions, conditions)));
    }

    private static MenuItemSpec item(List<Ref> actions, List<Ref> conditions) {
        List<Requirement> requirements =
                conditions.stream().map(ref -> new Requirement(ref, false)).toList();
        return new MenuItemSpec(
                SlotSet.parse(List.of("0"), 9),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                LoreMode.REPLACE,
                new RequirementSpec(requirements, requirements.size(), List.of()),
                new ClickSpec(Map.of(ClickKind.LEFT, actions), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE,
                Optional.empty());
    }
}
