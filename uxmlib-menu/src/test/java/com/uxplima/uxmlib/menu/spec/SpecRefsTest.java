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

    /**
     * An {@code input:} or {@code confirm:} step is the engine's own: it asks, then runs what follows. It is not a
     * word any plugin registers, so naming it as unregistered told an operator that a working prompt did nothing.
     * The word after it is still read.
     */
    @Test
    @DisplayName("an input or confirm step is the engine's own and is not named, and a misspelt action beside it is")
    void continuationStepsAreNotNamed() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                rows = 1
                items {
                  ask { slot = 0, material = STONE, click { any = [
                    { do = "input:member.name", prompt = "Name?" }, "shop:buyy" ] } }
                  sure { slot = 1, material = STONE, click { left = {
                    do = "confirm:reset", title = "Sure?", yes = ["shop:reset"], no = [] } } }
                }
                """);

        assertThat(SpecRefs.unknownActions(spec, Set.of("shop:buy", "shop:reset")::contains))
                .containsExactly("shop:buyy");
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

    @Test
    @DisplayName("a gesture's requirement block is read: its condition, its outcomes and its deny list")
    void aClickRequirementBlockIsRead() {
        Requirement gate = new Requirement(
                ref("shop:can-aford"), false, false, List.of(ref("shop:thank")), List.of(ref("shop:sorry")));
        RequirementSpec block = new RequirementSpec(List.of(gate), 1, List.of(ref("shop:deny")));
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(ClickKind.LEFT, block));
        MenuSpec spec = only(itemWithClick(click));

        assertThat(SpecRefs.unknownConditions(spec, id -> false)).containsExactly("shop:can-aford");
        assertThat(SpecRefs.unknownActions(spec, id -> false))
                .containsExactlyInAnyOrder("shop:thank", "shop:sorry", "shop:deny");
    }

    @Test
    @DisplayName("every link of a gesture's else chain is read, however deep")
    void anElseChainIsReadToItsEnd() {
        ClickBranch last = new ClickBranch(
                new RequirementSpec(List.of(new Requirement(ref("deep:cond"), false)), 1, List.of()),
                List.of(ref("deep:act")),
                Optional.empty());
        ClickBranch first = new ClickBranch(RequirementSpec.NONE, List.of(ref("near:act")), Optional.of(last));
        ClickSpec click = new ClickSpec(Map.of(), Map.of(), Map.of(), Map.of(ClickKind.LEFT, first));
        MenuSpec spec = only(itemWithClick(click));

        assertThat(SpecRefs.unknownConditions(spec, id -> false)).containsExactly("deep:cond");
        assertThat(SpecRefs.unknownActions(spec, id -> false)).containsExactlyInAnyOrder("near:act", "deep:act");
    }

    @Test
    @DisplayName("a list's row template is read like any other item")
    void aListTemplateIsRead() {
        MenuItemSpec template = item(List.of(ref("row:act")), List.of(ref("row:cond")));
        MenuItemSpec host = withList(item(List.of(), List.of()), new ListSpec(ref("rows"), template, 9, List.of()));
        MenuSpec spec = only(host);

        assertThat(SpecRefs.unknownActions(spec, id -> false)).containsExactly("row:act");
        assertThat(SpecRefs.unknownConditions(spec, id -> false)).containsExactly("row:cond");
    }

    @Test
    @DisplayName("a Bedrock form's submit list is read")
    void aBedrockSubmitListIsRead() {
        MenuSpec plain = only(item(List.of(), List.of()));
        MenuSpec spec = new MenuSpec(
                plain.title(),
                plain.rows(),
                plain.refresh(),
                List.of(),
                List.of(),
                List.of(),
                plain.items(),
                Optional.empty(),
                Map.of(),
                0L,
                false,
                false,
                Optional.of(new BedrockFormSpec("t", null, List.of(), List.of(ref("form:sumbit")))),
                Map.of());

        assertThat(SpecRefs.unknownActions(spec, id -> false)).containsExactly("form:sumbit");
    }

    @Test
    @DisplayName("the actions an item runs on a dropped item are read")
    void itemDragActionsAreRead() {
        MenuItemSpec base = item(List.of(), List.of());
        MenuItemSpec drop = new MenuItemSpec(
                base.slots(),
                base.priority(),
                base.material(),
                base.name(),
                base.lore(),
                base.decor(),
                base.loreMode(),
                base.view(),
                base.click(),
                base.update(),
                base.list(),
                base.type(),
                Optional.of(new ItemDragSpec(new ItemRuleSpec(List.of(), 1, ""), false, List.of(ref("drop:tkae")))),
                base.toPage());

        assertThat(SpecRefs.unknownActions(only(drop), id -> false)).containsExactly("drop:tkae");
    }

    private static Ref ref(String id) {
        return new Ref(id, Map.of());
    }

    private static MenuSpec only(MenuItemSpec item) {
        return new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of("only", item));
    }

    private static MenuItemSpec itemWithClick(ClickSpec click) {
        MenuItemSpec base = item(List.of(), List.of());
        return new MenuItemSpec(
                base.slots(),
                base.priority(),
                base.material(),
                base.name(),
                base.lore(),
                base.decor(),
                base.loreMode(),
                base.view(),
                click,
                base.update(),
                base.list(),
                base.type(),
                base.itemDrag(),
                base.toPage());
    }

    private static MenuItemSpec withList(MenuItemSpec base, ListSpec list) {
        return new MenuItemSpec(
                base.slots(),
                base.priority(),
                base.material(),
                base.name(),
                base.lore(),
                base.decor(),
                base.loreMode(),
                base.view(),
                base.click(),
                base.update(),
                Optional.of(list),
                base.type(),
                base.itemDrag(),
                base.toPage());
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
