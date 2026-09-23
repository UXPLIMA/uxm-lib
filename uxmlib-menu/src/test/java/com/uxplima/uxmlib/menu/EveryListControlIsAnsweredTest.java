package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.runtime.MenuControl;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.ListControlSyntax.SortDirection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A window may write {@code list-sort}, {@code list-filter}, {@code list-search}, {@code has-next} and
 * {@code has-previous} in any plugin, and {@link MenuBasics} answers them.
 *
 * <p>The window syntax had them and the engine carried them out, but nothing registered them. uxm-plots found every
 * sort, filter, search and tab button a dead click and 32 warnings at startup. A plugin that registered its own page
 * conditions after {@link MenuBasics}, as uxmAuction does, must keep its own and not stop the enable.
 */
class EveryListControlIsAnsweredTest {

    private MenuBindings bindings;
    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        bindings = new MenuBindings();
        MenuBasics.register(bindings);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the three list actions reach the window's list control")
    void theListActionsReachTheControl() {
        List<String> seen = new ArrayList<>();
        MenuControl control = recording(seen);

        fire("list-sort", "shelf:prev", control);
        fire("list-filter", "shelf:kind=tool", control);
        fire("list-search", "shelf:name", control);
        fire("list-sort", "pw:browse", control);
        fire("list-search", "shelf:name:@menu.find", control);

        assertThat(seen)
                .containsExactly(
                        "sort shelf PREVIOUS",
                        "filter shelf kind=tool",
                        "search shelf name ",
                        "sort pw:browse NEXT",
                        "search shelf name @menu.find");
    }

    @Test
    @DisplayName("a page arrow's conditions read the page the window is on")
    void thePageConditionsReadThePage() {
        MenuContext first = MenuContext.of(viewer, null, 0).withPageCount(3);
        MenuContext middle = MenuContext.of(viewer, null, 1).withPageCount(3);
        MenuContext last = MenuContext.of(viewer, null, 2).withPageCount(3);

        assertThat(holds("has-next", first)).isTrue();
        assertThat(holds("has-next", last)).isFalse();
        assertThat(holds("has-previous", first)).isFalse();
        assertThat(holds("has-previous", middle)).isTrue();
        assertThat(holds("has-prev", middle)).isTrue();
    }

    @Test
    @DisplayName("a plugin's own condition registered afterwards replaces the shared one and does not stop the enable")
    void aPluginsOwnConditionReplacesTheSharedOne() {
        bindings.condition("has-next", (ctx, args) -> true);
        bindings.action("close", ctx -> {});

        assertThat(holds("has-next", MenuContext.of(viewer, null, 2).withPageCount(3)))
                .isTrue();
        assertThatThrownBy(() -> bindings.condition("has-next", (ctx, args) -> false))
                .as("two plugin registrations under one name are still a wiring mistake")
                .isInstanceOf(IllegalStateException.class);
    }

    private void fire(String action, String value, MenuControl control) {
        MenuActionContext ctx = new MenuActionContext(
                MenuContext.of(viewer, null, 0), viewer, ClickKind.LEFT, Map.of("value", value), control);
        bindings.action(action).orElseThrow().accept(ctx);
    }

    private boolean holds(String condition, MenuContext ctx) {
        return bindings.condition(condition).orElseThrow().test(ctx, Map.of());
    }

    private static MenuControl recording(List<String> seen) {
        return new MenuControl() {
            @Override
            public void refresh() {}

            @Override
            public void refreshSlot(int slot) {}

            @Override
            public void resetPagination() {}

            @Override
            public void sortList(String listId, SortDirection direction) {
                seen.add("sort " + listId + " " + direction);
            }

            @Override
            public void filterList(String listId, String key, String value) {
                seen.add("filter " + listId + " " + key + "=" + value);
            }

            @Override
            public void searchList(String listId, String key) {
                seen.add("search " + listId + " " + key);
            }

            @Override
            public void searchList(String listId, String key, String prompt) {
                seen.add("search " + listId + " " + key + " " + prompt);
            }
        };
    }
}
