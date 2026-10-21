package com.uxplima.uxmlib.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockIcons;
import com.uxplima.uxmlib.bedrock.BedrockImage;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.menu.eval.Pagination;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.BedrockFormSpec;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.Ref;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The native forms a Bedrock viewer is shown in place of a chest: the automatic SimpleForm built from a menu's tiles
 * and lists, the explicit {@code bedrock {}} CustomForm, and the gesture form a tile with several gestures asks
 * through. A tap runs through the engine's own action runner, so it reaches the same handler a chest click would.
 */
@NullMarked
final class BedrockForms {

    /** Runs a list of action refs for a gesture, the way a chest click runs them. */
    @FunctionalInterface
    interface ActionRunner {
        void run(MenuContext ctx, List<Ref> refs, ClickKind kind);
    }

    private final MenuRenderer renderer;
    private final Scheduler scheduler;
    private final BedrockScreen bedrockScreen;
    private final BiFunction<MenuSpec, MenuContext, Map<String, List<?>>> listRows;
    private final ActionRunner actions;

    BedrockForms(
            MenuRenderer renderer,
            Scheduler scheduler,
            BedrockScreen bedrockScreen,
            BiFunction<MenuSpec, MenuContext, Map<String, List<?>>> listRows,
            ActionRunner actions) {
        this.renderer = renderer;
        this.scheduler = scheduler;
        this.bedrockScreen = bedrockScreen;
        this.listRows = listRows;
        this.actions = actions;
    }

    /**
     * Send the Bedrock viewer a native Cumulus SimpleForm standing in for the chest menu. The button list is built in
     * three runs: the spec's visible static items, then the current page's list entries, each one the list template
     * stamped per entry, then form-native Previous/Next buttons when the list spans more than one page. Each button is
     * paired with a
     * {@link Runnable} handler at the same index. The {@code onSelect} callback simply dispatches by index, so every
     * button's own handler owns its threading: a static or entry tap runs that item's click actions on the viewer's
     * entity thread, a page button re-resolves the list off-thread and re-sends. The form send is on the viewer's
     * entity thread (the open path already hopped here); the tap response arrives off-thread, and each handler makes
     * its own hop. The resolved list cache is threaded in so a list-backed menu shows its entries.
     */
    void sendSimpleForm(Player live, MenuSpec spec, MenuContext ctx, Map<String, List<?>> resolved) {
        Player viewer = ctx.viewer();
        List<BedrockButton> buttons = new ArrayList<>();
        List<Runnable> handlers = new ArrayList<>();
        appendStaticButtons(spec, ctx, viewer, buttons, handlers);
        int pageCount = appendListButtons(spec, ctx, viewer, resolved, buttons, handlers);
        appendPageButtons(spec, ctx, viewer, pageCount, buttons, handlers);
        bedrockScreen.sendSimpleForm(live, renderer.titleText(spec, ctx), null, buttons, index -> {
            if (index >= 0 && index < handlers.size()) {
                handlers.get(index).run();
            }
        });
    }

    /**
     * Send the Bedrock viewer the menu's explicit {@code bedrock {}} CustomForm: the form-native widgets
     * (label/input/dropdown/slider/toggle) the automatic SimpleForm degradation cannot express. The form's title, intro
     * content and every widget's display text and options are resolved through the renderer's plain-text path (they may
     * carry a {@code %token%}/{@code @key}, exactly like an item name), then handed to the screen. On submit, each
     * widget's value arrives keyed by its {@code name}, and {@code runOnSubmit} binds those as local placeholders and
     * runs the block's on-submit actions on the viewer's entity thread: Cumulus responds off-thread, so the hop is
     * explicit. Closing without submitting is a no-op: the viewer simply dismissed the form.
     */
    void sendCustomForm(Player live, MenuSpec spec, MenuContext ctx) {
        BedrockFormSpec form = spec.bedrock().orElseThrow();
        Player viewer = ctx.viewer();
        String title = renderer.plainText(form.title(), ctx);
        String content = form.content() == null ? null : renderer.plainText(form.content(), ctx);
        List<BedrockWidget> widgets = resolveWidgets(form.widgets(), ctx);
        bedrockScreen.sendCustomForm(
                live,
                title,
                content,
                widgets,
                values -> scheduler.entity(viewer, () -> runOnSubmit(spec, ctx, values)),
                () -> {
                    // The viewer dismissed the form without submitting; there is nothing to run, they just closed it.
                });
    }

    /** Resolve each widget's display text (and a dropdown's options) against {@code ctx}, keeping its binding name and numeric bounds. */
    private List<BedrockWidget> resolveWidgets(List<BedrockWidget> widgets, MenuContext ctx) {
        List<BedrockWidget> resolved = new ArrayList<>(widgets.size());
        for (BedrockWidget widget : widgets) {
            resolved.add(resolveWidget(widget, ctx));
        }
        return resolved;
    }

    /** One widget with its display strings resolved to plain text; the {@code name} and numeric bounds pass through unchanged. */
    private BedrockWidget resolveWidget(BedrockWidget widget, MenuContext ctx) {
        return switch (widget) {
            case BedrockWidget.Label label -> new BedrockWidget.Label(renderer.plainText(label.text(), ctx));
            case BedrockWidget.Input input ->
                new BedrockWidget.Input(
                        input.name(),
                        renderer.plainText(input.label(), ctx),
                        renderer.plainText(input.placeholder(), ctx),
                        renderer.plainText(input.defaultText(), ctx));
            case BedrockWidget.Dropdown dropdown ->
                new BedrockWidget.Dropdown(
                        dropdown.name(),
                        renderer.plainText(dropdown.label(), ctx),
                        resolveOptions(dropdown.options(), ctx),
                        dropdown.defaultIndex());
            case BedrockWidget.Slider slider ->
                new BedrockWidget.Slider(
                        slider.name(),
                        renderer.plainText(slider.label(), ctx),
                        slider.min(),
                        slider.max(),
                        slider.step(),
                        slider.defaultValue());
            case BedrockWidget.Toggle toggle ->
                new BedrockWidget.Toggle(toggle.name(), renderer.plainText(toggle.label(), ctx), toggle.defaultValue());
        };
    }

    /** Resolve every dropdown option string to plain text against {@code ctx}. */
    private List<String> resolveOptions(List<String> options, MenuContext ctx) {
        List<String> resolved = new ArrayList<>(options.size());
        for (String option : options) {
            resolved.add(renderer.plainText(option, ctx));
        }
        return resolved;
    }

    /**
     * Append one button per visible actionable static item, each tapping into that item's own left-click actions. The
     * button's icon is sourced from the item's own resolved material spec (a material → a Bedrock texture path, a
     * {@code skull:} → an mc-heads avatar URL), so a form button carries the same face the chest icon would.
     */
    private void appendStaticButtons(
            MenuSpec spec, MenuContext ctx, Player viewer, List<BedrockButton> buttons, List<Runnable> handlers) {
        for (MenuItemSpec item : renderer.visibleStaticItemsInSlotOrder(spec, ctx)) {
            buttons.add(formButton(item, ctx, viewer));
            handlers.add(() -> scheduler.entity(viewer, () -> tapped(viewer, ctx, item)));
        }
    }

    /** A form button for {@code item} rendered against {@code ctx}: its label plus an icon sourced from its spec. */
    private BedrockButton formButton(MenuItemSpec item, MenuContext ctx, Player viewer) {
        BedrockImage image = BedrockIcons.forMaterialSpec(renderer.materialSpec(item, ctx), viewer.getUniqueId());
        return new BedrockButton(renderer.buttonText(item, ctx), image);
    }

    /**
     * Append one button per list entry on the current page, labelling each with the list template stamped for that
     * entry and routing a tap through the template's click actions bound to that entry: the form stand-in for a chest
     * list cell's {@code RenderedSlot(template, entry)}. Every list the window draws adds its rows, in the order of
     * the slot each starts at, each cut to the page the chest would show; a window may lay out more than one, as a
     * shop shows the shelves under the current one above its products, and a form that read only the paged list left
     * the other out. The page count is the longest list's, since every list turns with the page as it does on a
     * chest; a static-only menu has none and stays a single page. Returns the page count so the caller
     * knows whether to add page-nav buttons.
     *
     * <p>The entries come through {@link MenuRenderer#visibleListEntries}, so the {@code view} block on the list item
     * and the one on its template are read here exactly as they are on a chest.
     */
    private int appendListButtons(
            MenuSpec spec,
            MenuContext ctx,
            Player viewer,
            Map<String, List<?>> resolved,
            List<BedrockButton> buttons,
            List<Runnable> handlers) {
        Optional<MenuItemSpec> paged = firstListItem(spec);
        if (paged.isEmpty()) {
            return 1;
        }
        // Every list is turned with the page, as on a chest, so the form pages as far as the longest one goes.
        int pageCount = 1;
        for (MenuItemSpec listItem : listItemsInDrawOrder(spec)) {
            pageCount = Math.max(pageCount, appendRowsOfOneList(listItem, ctx, viewer, resolved, buttons, handlers));
        }
        return pageCount;
    }

    /** One list's rows on the current page as buttons; returns that list's page count. */
    private int appendRowsOfOneList(
            MenuItemSpec listItem,
            MenuContext ctx,
            Player viewer,
            Map<String, List<?>> resolved,
            List<BedrockButton> buttons,
            List<Runnable> handlers) {
        // Through the renderer, so a form reads the same two view gates a chest does: the list item's own,
        // and the template's, asked once per row. A row a Java viewer cannot see is not a form button.
        List<?> entries = renderer.visibleListEntries(listItem, ctx, resolved);
        MenuItemSpec template = listItem.list().orElseThrow().template();
        @SuppressWarnings("unchecked") // a list source's element type is opaque to the engine; entries flow as Object
        Pagination.Page<Object> page =
                Pagination.paginate((List<Object>) entries, listItem.slots().slots(), ctx.page());
        for (Map.Entry<Integer, Object> placement : page.placements()) {
            MenuContext entryCtx = ctx.withEntry(placement.getValue());
            buttons.add(formButton(template, entryCtx, viewer));
            handlers.add(() -> scheduler.entity(viewer, () -> tapped(viewer, entryCtx, template)));
        }
        return page.pageCount();
    }

    /** Every list-backed item of {@code spec}, by the lowest slot each draws into: the order a chest shows them. */
    private static List<MenuItemSpec> listItemsInDrawOrder(MenuSpec spec) {
        List<MenuItemSpec> listItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            if (item.list().isPresent()) {
                listItems.add(item);
            }
        }
        listItems.sort(java.util.Comparator.comparingInt(item ->
                item.slots().slots().stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE)));
        return listItems;
    }

    /**
     * Append the form-native Previous/Next buttons a paged list needs: a Previous when the viewer is past page zero, a
     * Next when a further page exists, each re-sending the form one page over. These are text-only (no icon: a page
     * control needs none). A single-page menu (a static-only menu or a list that fits one page) adds neither.
     */
    private void appendPageButtons(
            MenuSpec spec,
            MenuContext ctx,
            Player viewer,
            int pageCount,
            List<BedrockButton> buttons,
            List<Runnable> handlers) {
        if (ctx.page() > 0) {
            buttons.add(new BedrockButton(renderer.plainMessage(viewer, MenuKeys.PAGE_PREVIOUS), null));
            handlers.add(() -> resendBedrockPage(viewer, spec, ctx, ctx.page() - 1));
        }
        if (ctx.page() + 1 < pageCount) {
            buttons.add(new BedrockButton(renderer.plainMessage(viewer, MenuKeys.PAGE_NEXT), null));
            handlers.add(() -> resendBedrockPage(viewer, spec, ctx, ctx.page() + 1));
        }
    }

    /**
     * The list-backed item the form's page buttons drive, empty when the menu carries no list. The choice lives in
     * {@link MenuSpec#pagedListItem()} so a Bedrock viewer pages the same list a Java viewer does.
     */
    private static Optional<MenuItemSpec> firstListItem(MenuSpec spec) {
        return spec.pagedListItem();
    }

    /**
     * Re-send the Bedrock form one page over: the target a Previous/Next form button runs. Because a list source may
     * read a database, the list is re-resolved off the tick thread for the new page, then the render hops back onto the
     * viewer's entity thread, the very async-resolve→entity-render discipline the initial open takes. The viewer may
     * have gone offline between the tap and the re-render, so the online player is re-fetched and a missing one
     * skipped.
     */
    private void resendBedrockPage(Player viewer, MenuSpec spec, MenuContext baseCtx, int page) {
        MenuContext newCtx = baseCtx.withPage(Math.max(0, page));
        scheduler.async(() -> {
            Map<String, List<?>> rows = listRows.apply(spec, newCtx);
            scheduler.entity(viewer, () -> {
                if (viewer.isOnline()) {
                    sendSimpleForm(viewer, spec, newCtx, rows);
                }
            });
        });
    }

    /**
     * What a tap on a tile does: run its one gesture, or ask which of its several.
     *
     * <p>A chest gives a Java viewer six gestures on one square and a form gives a Bedrock viewer a tap. Running the
     * left click and nothing else made every other gesture a feature a Bedrock player could not reach: a shop line
     * they could not restock, a quest they could not reroll, a line they could not take off. A tile that binds one
     * gesture runs it, whichever one it is, and a tile that binds several is asked about.
     */
    private void tapped(Player live, MenuContext ctx, MenuItemSpec item) {
        List<ClickKind> gestures = boundGestures(item);
        if (gestures.size() > 1) {
            sendGestureForm(live, ctx, item, gestures);
            return;
        }
        ClickKind only = gestures.isEmpty() ? ClickKind.LEFT : gestures.get(0);
        actions.run(ctx, item.click().actionsFor(only), only);
    }

    /**
     * The gestures this tile binds, in the order a form lists them.
     *
     * <p>{@link ClickKind#ANY} is left out: it is not a gesture a viewer chooses but a list every gesture carries,
     * and {@code ClickSpec#actionsFor} merges it into whichever one runs. A tile whose only binding is ANY therefore
     * has no gesture of its own and runs on the tap, which is what it asks for.
     */
    private static List<ClickKind> boundGestures(MenuItemSpec item) {
        List<ClickKind> bound = new ArrayList<>();
        for (ClickKind kind : ClickKind.values()) {
            List<Ref> own = item.click().actions().getOrDefault(kind, List.of());
            if (kind != ClickKind.ANY && !own.isEmpty()) {
                bound.add(kind);
            }
        }
        return bound;
    }

    /**
     * Ask a Bedrock viewer which gesture they meant, then run it.
     *
     * <p>One button per gesture, named by a word the host's catalogue answers, so the question is in the language
     * the viewer reads. Cumulus answers off the server thread, so the chosen gesture hops back onto the viewer's
     * entity thread before it runs, the same hop the first form's tap takes.
     */
    private void sendGestureForm(Player live, MenuContext ctx, MenuItemSpec item, List<ClickKind> gestures) {
        Player viewer = ctx.viewer();
        List<BedrockButton> buttons = new ArrayList<>();
        for (ClickKind kind : gestures) {
            String label = item.click()
                    .nameFor(kind)
                    .map(name -> renderer.plainText(name, ctx))
                    .orElseGet(() -> renderer.plainMessage(viewer, gestureKey(kind)));
            buttons.add(new BedrockButton(label, null));
        }
        bedrockScreen.sendSimpleForm(
                live, renderer.plainMessage(viewer, MenuKeys.GESTURE_TITLE), null, buttons, index -> {
                    if (index < 0 || index >= gestures.size()) {
                        return;
                    }
                    ClickKind chosen = gestures.get(index);
                    scheduler.entity(viewer, () -> actions.run(ctx, item.click().actionsFor(chosen), chosen));
                });
    }

    /** The word that names one gesture on the form. */
    private static String gestureKey(ClickKind kind) {
        return switch (kind) {
            case LEFT -> MenuKeys.GESTURE_LEFT;
            case RIGHT -> MenuKeys.GESTURE_RIGHT;
            case SHIFT_LEFT -> MenuKeys.GESTURE_SHIFT_LEFT;
            case SHIFT_RIGHT -> MenuKeys.GESTURE_SHIFT_RIGHT;
            case MIDDLE -> MenuKeys.GESTURE_MIDDLE;
            case DROP -> MenuKeys.GESTURE_DROP;
            case CONTROL_DROP -> MenuKeys.GESTURE_CONTROL_DROP;
            case DOUBLE_CLICK -> MenuKeys.GESTURE_DOUBLE_CLICK;
            case ANY -> MenuKeys.GESTURE_LEFT;
        };
    }

    /**
     * Run the {@code bedrock {}} block's on-submit actions with the submitted widget values bound. Each value arrives
     * keyed by its widget {@code name}; they are layered over the menu's own {@code placeholders {}} block (the values
     * winning) and carried as the context's local placeholders, so a {@code %warpname%}/{@code %cost%} token in an on-
     * submit action's argument resolves to the submitted value through the local-placeholder channel: exactly the way
     * the click path expands an item-drag's {@code %drag_*%} tokens. Runs on the viewer's entity thread the caller
     * already hopped onto, through the same shared engine's action runner a form tap uses.
     */
    private void runOnSubmit(MenuSpec spec, MenuContext ctx, Map<String, String> values) {
        MenuContext submitCtx = ctx.withLocalPlaceholders(merge(spec.placeholders(), values));
        actions.run(submitCtx, spec.bedrock().orElseThrow().onSubmit(), ClickKind.LEFT);
    }

    /** The menu's own local placeholders with the submitted values layered on top, the submitted value winning a clash. */
    private static Map<String, String> merge(Map<String, String> base, Map<String, String> overrides) {
        if (base.isEmpty()) {
            return overrides;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }
}
