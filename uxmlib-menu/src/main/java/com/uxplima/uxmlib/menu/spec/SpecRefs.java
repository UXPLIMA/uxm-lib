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
 * more places than it looks: a menu's own open and close lists, every item's click actions per gesture,
 * every item's view requirements and their success and deny lists, and the menu's open requirement.
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

    /** Every action a spec can run: its own two lists, and each item's clicks and requirement outcomes. */
    private static List<Ref> actionRefs(MenuSpec spec) {
        List<Ref> refs = new ArrayList<>(spec.openActions());
        refs.addAll(spec.closeActions());
        for (MenuItemSpec item : spec.items().values()) {
            item.click().actions().values().forEach(refs::addAll);
            refs.addAll(item.view().deny());
            for (Requirement requirement : item.view().requirements()) {
                refs.addAll(requirement.success());
                refs.addAll(requirement.deny());
            }
        }
        return refs;
    }

    /** Every condition a spec can test: the open requirement, each item's view block and its click gates. */
    private static List<Ref> conditionRefs(MenuSpec spec) {
        List<Ref> refs = new ArrayList<>(spec.openRequirement());
        for (MenuItemSpec item : spec.items().values()) {
            for (Requirement requirement : item.view().requirements()) {
                refs.add(requirement.condition());
            }
            item.click().conditions().values().forEach(refs::addAll);
        }
        return refs;
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
