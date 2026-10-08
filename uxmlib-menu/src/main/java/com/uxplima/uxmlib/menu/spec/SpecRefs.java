package com.uxplima.uxmlib.menu.spec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Every id a menu file names, so the engine can say which of them nobody answers.
 *
 * <p>A menu is a file an operator edits and an id is a word they type. The engine looks each one up at
 * the moment it needs it, and until 2026-09-22 it found nothing and said nothing: an action id nobody
 * registered was a button that did nothing, and a condition id nobody registered hid the tile it
 * guarded. The hidden tile is the worse of the two to find, because it produces no click, so the
 * warning the click path prints can never reach it.
 *
 * <p>The ids are collected rather than checked here: whether one is registered is the caller's
 * question, and the caller holds the registries. What this knows is where a spec keeps them, which is
 * more places than it looks: a menu's own open and close lists and its Bedrock submit list, the open
 * requirement, and for every item its click actions per gesture, each gesture's requirement block and
 * else chain, its view requirements with their success and deny lists, what it runs on a dropped
 * item, and all of that again for a list's row template. Until 2026-10-08 the blocks, the chains, the
 * drop actions, the template and the submit list were not read, so a typo there stayed silent.
 *
 * <p>Resolution is the engine's own, through {@link Ref#resolve}, so a namespaced id that carries a
 * value is not reported: {@code auction:sort:newest} finds {@code auction:sort} and is answered.
 */
public final class SpecRefs {

    private SpecRefs() {}

    /** The action ids {@code spec} names that {@code registered} does not answer, in file order, once each. */
    public static List<String> unknownActions(MenuSpec spec, Predicate<String> registered) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(registered, "registered");
        return unknown(actionRefs(spec), registered);
    }

    /** The condition ids {@code spec} names that {@code registered} does not answer, in file order, once each. */
    public static List<String> unknownConditions(MenuSpec spec, Predicate<String> registered) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(registered, "registered");
        return unknown(conditionRefs(spec), registered);
    }

    /** Every action a spec can run: its own two lists, its Bedrock submit list, and each item's. */
    private static List<Ref> actionRefs(MenuSpec spec) {
        List<Ref> refs = new ArrayList<>(spec.openActions());
        refs.addAll(spec.closeActions());
        spec.bedrock().ifPresent(form -> refs.addAll(form.onSubmit()));
        for (MenuItemSpec item : spec.items().values()) {
            itemActions(item, refs);
        }
        return refs;
    }

    /**
     * An item's actions: each gesture's list, requirement blocks and else chain, its view block's outcomes, what it runs
     * on a dropped item, and the
     * same again for a list's row template, which the engine runs exactly as it runs a fixed item.
     */
    private static void itemActions(MenuItemSpec item, List<Ref> refs) {
        ClickSpec click = item.click();
        click.actions().values().forEach(refs::addAll);
        click.requirements().values().forEach(block -> blockActions(block, refs));
        for (ClickBranch branch : click.orElse().values()) {
            for (ClickBranch link = branch; link != null; link = link.orElse().orElse(null)) {
                refs.addAll(link.actions());
                blockActions(link.requirement(), refs);
            }
        }
        blockActions(item.view(), refs);
        item.itemDrag().ifPresent(drag -> refs.addAll(drag.actions()));
        item.list().ifPresent(list -> itemActions(list.template(), refs));
    }

    private static void blockActions(RequirementSpec block, List<Ref> refs) {
        refs.addAll(block.deny());
        for (Requirement requirement : block.requirements()) {
            refs.addAll(requirement.success());
            refs.addAll(requirement.deny());
        }
    }

    /** Every condition a spec can test: the open requirement and each item's. */
    private static List<Ref> conditionRefs(MenuSpec spec) {
        List<Ref> refs = new ArrayList<>(spec.openRequirement());
        for (MenuItemSpec item : spec.items().values()) {
            itemConditions(item, refs);
        }
        return refs;
    }

    /** An item's conditions: its view block, each gesture's gates, requirement blocks and else chain, and its row template. */
    private static void itemConditions(MenuItemSpec item, List<Ref> refs) {
        ClickSpec click = item.click();
        blockConditions(item.view(), refs);
        click.conditions().values().forEach(refs::addAll);
        click.requirements().values().forEach(block -> blockConditions(block, refs));
        for (ClickBranch branch : click.orElse().values()) {
            for (ClickBranch link = branch; link != null; link = link.orElse().orElse(null)) {
                blockConditions(link.requirement(), refs);
            }
        }
        item.list().ifPresent(list -> itemConditions(list.template(), refs));
    }

    private static void blockConditions(RequirementSpec block, List<Ref> refs) {
        for (Requirement requirement : block.requirements()) {
            refs.add(requirement.condition());
        }
    }

    private static List<String> unknown(List<Ref> refs, Predicate<String> registered) {
        Set<String> found = new LinkedHashSet<>();
        for (Ref ref : refs) {
            Ref effective = ref.resolve(registered);
            if (!registered.test(effective.id())) {
                found.add(ref.id());
            }
        }
        return List.copyOf(found);
    }
}
