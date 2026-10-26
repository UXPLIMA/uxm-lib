package com.uxplima.uxmlib.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockIcons;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.gui.style.MenuTitles;
import com.uxplima.uxmlib.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.eval.PageRequest;
import com.uxplima.uxmlib.menu.eval.PagedResult;
import com.uxplima.uxmlib.menu.property.ChildClickHandler;
import com.uxplima.uxmlib.menu.property.ConfirmOpener;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.property.SelectorButton;
import com.uxplima.uxmlib.menu.property.SelectorOpener;
import com.uxplima.uxmlib.menu.render.ConfirmLook;
import com.uxplima.uxmlib.menu.render.ConfirmRenderer;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.render.GridRenderer;
import com.uxplima.uxmlib.menu.render.ListViewRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.render.SelectorRenderer;
import com.uxplima.uxmlib.menu.runtime.ActionArguments;
import com.uxplima.uxmlib.menu.runtime.ConfirmState;
import com.uxplima.uxmlib.menu.runtime.EditorRefresh;
import com.uxplima.uxmlib.menu.runtime.EditorState;
import com.uxplima.uxmlib.menu.runtime.GridViewState;
import com.uxplima.uxmlib.menu.runtime.LastMenu;
import com.uxplima.uxmlib.menu.runtime.ListQueryState;
import com.uxplima.uxmlib.menu.runtime.ListViewState;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.runtime.MenuRefresh;
import com.uxplima.uxmlib.menu.runtime.PagedListRows;
import com.uxplima.uxmlib.menu.runtime.PagedListView;
import com.uxplima.uxmlib.menu.runtime.SelectorState;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.ListSpec;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.Ref;
import com.uxplima.uxmlib.menu.spec.RefreshSpec;
import com.uxplima.uxmlib.menu.spec.SpecRefs;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point a feature uses to open a registered menu for a viewer. A feature registers its specs once at
 * wiring time, then calls {@link #open} to show one to a player. The façade first resolves every list source the spec
 * names off any tick thread (a list source may read a database, which must never block the viewer's region thread on
 * Folia) then hops onto the viewer's entity thread (where the live inventory may legally be touched), builds the {@link
 * MenuHolder} that owns every per-open piece of state, caches the resolved lists on it, renders the spec into a fresh
 * inventory the holder backs, and arms the refresh task. Pagination and refresh re-render from the holder's cache, so a
 * page flip never re-queries. The click listener recovers all of this from the window alone, so no player-keyed side
 * map is needed.
 */
public final class Menus {

    /** Operator diagnostics for a menu that names an inventory type the server rejects: logged, then a chest opens. */
    private static final Logger LOG = Logger.getLogger(Menus.class.getName());

    private final MenuRenderer renderer;
    private final Scheduler scheduler;
    private final ListSourceRegistry lists;

    /**
     * The streaming counterpart to {@link #lists}: the sources that answer for one page of a large corpus rather than
     * handing the whole of it over. Empty on every engine wired without one (a list/spec-only test fixture and any
     * production menu set with no paged source) in which case {@link #resolveLists} finds every source in {@link
     * #lists} and never consults this registry.
     */
    private final PagedListSourceRegistry pagedLists;

    /**
     * The editor renderer, present only on an engine wired for typed property editors. A list/spec-only engine (most
     * test fixtures) leaves it null and never calls {@link #openEditor}; opening an editor on such an engine is a
     * wiring error that fails loudly rather than half-rendering.
     */
    @Nullable private final EditorRenderer editorRenderer;

    /**
     * The action registry an open runs a spec's {@code open-actions} through, and the condition registry it gates a
     * spec's {@code open-requirement} on. Both are null on an engine wired without them (every list/spec-only test
     * fixture) in which case an open neither gates nor fires open-actions.
     * Only production wiring, which has the fully populated registries, passes them; a spec with no open-
     * requirement/open-actions is unaffected either way.
     */
    @Nullable private final ActionRegistry openActionRegistry;

    @Nullable private final ConditionRegistry openConditionRegistry;

    /**
     * The per-player reopen tracker {@code /menu last} reads. Null on every engine wired without it (every list/spec-
     * only test fixture) in which case an open records nothing. Only
     * production wiring, which builds one tracker and shares it with the {@code /menu} command, passes it; and even
     * then only a subject-less open (a custom menu) is remembered, in {@link #rememberLastOpen}.
     */
    @Nullable private final LastMenu lastMenu;

    /**
     * Whether a viewer is a Bedrock (Floodgate) player, so an engine menu can be redirected to a native form for them
     * rather than a chest. Defaults to {@link BedrockDetector#NONE} (always Java) on every engine wired without it, so
     * an open there falls straight through to the chest path. Only
     * production wiring, on a server with Floodgate, passes a detector that ever answers {@code true}.
     */
    private final BedrockDetector bedrock;

    /**
     * The screen that sends a Bedrock viewer a native Cumulus form. Defaults to {@link BedrockScreen#NONE} (a no-op
     * carrying no SDK reference) on every engine wired without it, and it is only ever reached after {@link #bedrock}
     * has confirmed a Bedrock viewer, so a Java-only engine never sends a form and never loads the Cumulus SDK.
     */
    private final BedrockScreen bedrockScreen;

    /** Where the confirm window puts its two buttons and which blocks they are: the host's look, or the standard. */
    private final ConfirmLook confirmLook;

    /** Paints the two-button confirm window; stateless, so one instance serves every confirm open. */
    private final ConfirmRenderer confirmRenderer = new ConfirmRenderer();

    /** Paints a selector child window; stateless, so one instance serves every picker open. */
    private final SelectorRenderer selectorRenderer = new SelectorRenderer();

    /** Paints a paginated entity list; stateless, so one instance serves every list open. */
    private final ListViewRenderer listViewRenderer = new ListViewRenderer();

    /** The opener a property's click hook calls to show its picker as an engine child window; wraps this façade. */
    private final SelectorOpener selectorOpener = this::openSelector;

    /** The opener a property's click hook calls to gate a removal behind an engine confirm child; wraps this façade. */
    private final ConfirmOpener confirmOpener = this::confirm;

    private final Map<String, MenuSpec> specs = new ConcurrentHashMap<>();

    /** The native forms a Bedrock viewer is shown in place of a chest. */
    private final BedrockForms forms;

    /**
     * The specs whose ids have been read back against the registries, so it is said once and not once
     * per open. Checked at the first open rather than at registration, because that is the only moment
     * both halves exist: a plugin may register a spec before it registers the vocabulary the spec
     * names, and a check at render would repeat several times a second.
     */
    private final Set<String> refsChecked = ConcurrentHashMap.newKeySet();

    /**
     * An engine over a renderer, a scheduler and the list sources, with nothing optional: no typed editors, no open
     * gate or open actions, no reopen tracker, no Bedrock form and no paged source. {@link #builder} adds them.
     */
    public Menus(MenuRenderer renderer, Scheduler scheduler, ListSourceRegistry lists) {
        this(builder(renderer, scheduler, lists));
    }

    private Menus(Builder builder) {
        this.renderer = builder.renderer;
        this.scheduler = builder.scheduler;
        this.lists = builder.lists;
        this.editorRenderer = builder.editorRenderer;
        this.openActionRegistry = builder.openActionRegistry;
        this.openConditionRegistry = builder.openConditionRegistry;
        this.lastMenu = builder.lastMenu;
        this.bedrock = builder.bedrock;
        this.bedrockScreen = builder.bedrockScreen;
        this.confirmLook = builder.confirmLook;
        this.pagedLists = builder.pagedLists;
        this.forms = new BedrockForms(
                renderer,
                scheduler,
                bedrockScreen,
                (spec, ctx) -> resolveLists(spec, ctx).rows(),
                this::runActions);
    }

    /** An engine over the three parts every menu needs, with each optional part set on the builder. */
    public static Builder builder(MenuRenderer renderer, Scheduler scheduler, ListSourceRegistry lists) {
        return new Builder(renderer, scheduler, lists);
    }

    /**
     * The optional parts of a {@link Menus}. Each one left unset is the absence its field documents: an open skips
     * the requirement gate and runs no open actions, records no reopen target, never redirects to a Bedrock form, and
     * resolves every list through the in-memory sources.
     */
    public static final class Builder {

        private final MenuRenderer renderer;
        private final Scheduler scheduler;
        private final ListSourceRegistry lists;
        private @Nullable EditorRenderer editorRenderer;
        private @Nullable ActionRegistry openActionRegistry;
        private @Nullable ConditionRegistry openConditionRegistry;
        private @Nullable LastMenu lastMenu;
        private BedrockDetector bedrock = BedrockDetector.NONE;
        private BedrockScreen bedrockScreen = BedrockScreen.NONE;
        private ConfirmLook confirmLook = ConfirmLook.STANDARD;
        private PagedListSourceRegistry pagedLists = new PagedListSourceRegistry();

        private Builder(MenuRenderer renderer, Scheduler scheduler, ListSourceRegistry lists) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.lists = Objects.requireNonNull(lists, "lists");
        }

        /** The renderer a typed property editor is drawn through. */
        public Builder editorRenderer(@Nullable EditorRenderer editorRenderer) {
            this.editorRenderer = editorRenderer;
            return this;
        }

        /** The registries an open runs a spec's {@code open-actions} through and gates its {@code open-requirement} on. */
        public Builder openRegistries(@Nullable ActionRegistry actions, @Nullable ConditionRegistry conditions) {
            this.openActionRegistry = actions;
            this.openConditionRegistry = conditions;
            return this;
        }

        /** The tracker that remembers the last menu a player opened, for a reopen command to read. */
        public Builder lastMenu(@Nullable LastMenu lastMenu) {
            this.lastMenu = lastMenu;
            return this;
        }

        /** The detector and screen an open consults to show a Bedrock viewer a native form instead of a chest. */
        public Builder bedrock(BedrockDetector detector, BedrockScreen screen) {
            this.bedrock = Objects.requireNonNull(detector, "detector");
            this.bedrockScreen = Objects.requireNonNull(screen, "screen");
            return this;
        }

        /** How the confirm window looks; {@link ConfirmLook#STANDARD} when the host names none. */
        public Builder confirmLook(ConfirmLook confirmLook) {
            this.confirmLook = Objects.requireNonNull(confirmLook, "confirmLook");
            return this;
        }

        /** The sources that answer for one page of a large list rather than the whole of it. */
        public Builder pagedLists(PagedListSourceRegistry pagedLists) {
            this.pagedLists = Objects.requireNonNull(pagedLists, "pagedLists");
            return this;
        }

        /** The engine. */
        public Menus build() {
            return new Menus(this);
        }
    }

    /** Registers a parsed spec under its id; a feature does this once at wiring time. */
    public void registerSpec(String id, MenuSpec spec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        specs.put(id, spec);
    }

    /**
     * Drop the spec registered under {@code id} so the menu can no longer be opened: the unregister half of the menu
     * editor's delete, run once the file is gone. A no-op when no spec carries that id. The loader's single-file reload
     * cannot do this itself (a deleted file is a not-found reload, not an unregister), so the editor calls it directly.
     */
    public void unregisterSpec(String id) {
        Objects.requireNonNull(id, "id");
        specs.remove(id);
    }

    /**
     * The spec registered under {@code id}, or empty when none is: a read-only lookup the {@code /menu dump} and {@code
     * /menu meta} operator diagnostics use to describe a loaded menu without opening it.
     */
    public Optional<MenuSpec> registeredSpec(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(specs.get(id));
    }

    /**
     * Run one menu {@code action} for {@code target} as if they had tapped it: the {@code /menu execute <player>
     * <action>} admin tool. It hops onto the target's own entity thread (where touching the live inventory is legal)
     * and dispatches through the same shared {@link #runActions} runner a form tap uses, with {@code target} as the
     * context viewer so a {@code %player%} in the action resolves to them. No spec need be registered: the action runs
     * standalone. An engine wired without an action registry (a list/spec-only test engine) runs nothing here, matching
     * {@link #runActions}.
     */
    public void execute(Player target, Ref action) {
        execute(target, action, Map.of());
    }

    /**
     * Run one menu {@code action} for {@code target} with {@code arguments} bound, so an {@code %argument_<name>%}
     * token inside the action resolves from a caller-supplied map rather than from an open window. This is what lets
     * a config-declared command (a custom command, or a menu's own open command) run the shared action vocabulary
     * with its typed arguments in hand and no inventory in sight.
     */
    public void execute(Player target, Ref action, Map<String, String> arguments) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(arguments, "arguments");
        scheduler.entity(target, () -> runActions(MenuContext.of(target, null, 0, arguments), List.of(action)));
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer}, carrying {@code subject} as the domain
     * object the menu is about (or null for a subject-less menu). An unknown spec id is a coding error in the
     * caller's wiring, so it fails loudly here rather than opening an empty window a player would meet.
     */
    public void open(Player viewer, String specId, @Nullable Object subject) {
        open(viewer, specId, subject, 0);
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer} at {@code page}, the same as {@link
     * #open(Player, String, Object)} but starting on a chosen page rather than page zero: what an {@code open:<menu>
     * [page]} action reaches for. A negative page is clamped to zero. An unknown spec id is a caller wiring error, so
     * it fails loudly here rather than opening an empty window.
     */
    public void open(Player viewer, String specId, @Nullable Object subject, int page) {
        open(viewer, specId, subject, page, Map.of());
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer} at {@code page}, carrying the typed command
     * {@code arguments} the menu was opened with: an operator {@code command {}} block's {@code %argument_<name>%}
     * values, keyed by argument name. The arguments ride the {@link MenuContext} to the renderer so a title, item name
     * or lore can expand them. A negative page is clamped to zero; an unknown spec id fails loudly here.
     */
    public void open(Player viewer, String specId, @Nullable Object subject, int page, Map<String, String> arguments) {
        openInternal(viewer, specId, subject, page, arguments, Map.of(), true);
    }

    /**
     * Open the spec for {@code viewer}, the player who <em>sees</em> the menu, attaching {@code passthrough} to the
     * open: values the host wants its own bindings to read and the engine never looks inside. This is the overload a
     * host reaches for when the open carries something the engine has no concept of, such as who triggered it when
     * that is not the viewer. The values ride the {@link MenuContext} through every page flip and refresh. A negative
     * page is clamped to zero; an unknown spec id fails loudly here.
     */
    public void open(
            Player viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            Map<String, Object> passthrough) {
        openInternal(viewer, specId, subject, page, arguments, passthrough, true);
    }

    /**
     * The shared open body every public {@link #open} overload and the internal {@link #reopen} route through. It
     * resolves the spec, clamps the page, resolves list sources off the tick thread, then shows the window on the
     * viewer's entity thread. {@code passthrough} is whatever the host attached to this open, carried onto the context
     * unread. {@code record} decides whether the open joins the viewer's {@code /menu last} / back history: a fresh
     * open records (subject permitting), a back-step or reopen-last replays an already-recorded open and must not push
     * it again: otherwise stepping back would immediately re-stack what it just popped.
     */
    private void openInternal(
            Player viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            Map<String, Object> passthrough,
            boolean record) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(passthrough, "passthrough");
        MenuSpec spec = specs.get(specId);
        if (spec == null) {
            throw new IllegalArgumentException("no menu spec registered under id: " + specId);
        }
        warnUnknownRefsOnce(specId, spec);
        int startPage = Math.max(0, page);
        Map<String, String> args = Map.copyOf(arguments);
        MenuContext ctx = MenuContext.of(viewer, subject, startPage, args)
                .withLocalPlaceholders(spec.placeholders())
                .withPassthrough(passthrough);
        scheduler.async(() -> {
            ResolvedLists resolved = resolveLists(spec, ctx);
            scheduler.entity(
                    viewer,
                    () -> openResolved(viewer, specId, spec, subject, resolved, startPage, args, passthrough, record));
        });
    }

    /**
     * Reopen the previous menu {@code viewer} had open: the target a {@code back} button steps to. It hops to the
     * viewer's entity thread and, if nothing remains beneath the current open (they are at the root, or the engine was
     * wired without a history), closes the window instead. A previous open whose spec is no longer registered (a since-
     * dropped menu) is treated as nothing-below, so a stale entry closes cleanly rather than raising the loud unknown-
     * spec failure a blind reopen would. The reopen itself replays the recorded open without re-recording it, so
     * stepping back never grows the history.
     */
    public void back(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            closeFor(viewer);
            return;
        }
        lastMenu.back(viewer.getUniqueId())
                .filter(prev -> specs.containsKey(prev.menuId()))
                .ifPresentOrElse(prev -> reopen(viewer, prev), () -> closeFor(viewer));
    }

    /**
     * Reopen the menu {@code viewer} currently has on top of their history: what {@code /menu last} runs. Returns
     * {@code false} (so the caller can show its own feedback) when there is nothing to reopen: the engine carries no
     * history, none has been recorded, or the recorded spec is no longer registered. A successful reopen replays the
     * recorded open without re-recording it, so calling it repeatedly never stacks duplicates.
     */
    public boolean reopenLast(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            return false;
        }
        Optional<LastMenu.LastOpen> last =
                lastMenu.get(viewer.getUniqueId()).filter(open -> specs.containsKey(open.menuId()));
        last.ifPresent(open -> reopen(viewer, open));
        return last.isPresent();
    }

    /**
     * Replay a recorded open (same page and typed arguments, always subject-less) without recording it again. The
     * history records no passed-through values and the reopen attaches none, deliberately: they are host objects of any
     * type, and holding them for the life of a back history would keep a logged-off player or a closed resource alive.
     * A binding that reads one must therefore tolerate its absence, which is what {@link MenuContext#passthrough()} is
     * for; the typed reader fails loudly here instead.
     */
    private void reopen(Player viewer, LastMenu.LastOpen open) {
        openInternal(viewer, open.menuId(), null, open.page(), open.arguments(), Map.of(), false);
    }

    /**
     * Close whatever window {@code viewer} has open, on their entity thread: the back-step to nothing / null-history
     * path.
     */
    private void closeFor(Player viewer) {
        scheduler.entity(viewer, () -> {
            if (viewer.isOnline()) {
                viewer.closeInventory();
            }
        });
    }

    /**
     * The menu {@code viewer} currently has open, as a plain {@link OpenMenuInfo} value, or empty when they are in no
     * engine menu: what the outbound {@code menu_*} placeholder source reads. It is a best-effort live read: it
     * resolves the online player and inspects the holder backing their open top inventory, which is the engine's single
     * source of truth for an open menu (no player-keyed side map is kept, so nothing can leak and there is nothing else
     * to consult). The read is authoritative when the placeholder resolves on the viewer's own region/main context; a
     * cross-region read on Folia would touch another region's player and is caught and degraded to empty, so a stray
     * off-region request reads "not in a menu" rather than throwing.
     */
    public Optional<OpenMenuInfo> currentMenu(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        try {
            Player live = Bukkit.getPlayer(viewer);
            if (live == null) {
                return Optional.empty();
            }
            InventoryHolder holder = live.getOpenInventory().getTopInventory().getHolder();
            return holder instanceof MenuHolder menu ? Optional.of(openMenuInfo(menu)) : Optional.empty();
        } catch (RuntimeException offRegion) {
            return Optional.empty();
        }
    }

    /**
     * The spec id of the engine menu backing {@code inventory}, or empty when it is not an engine menu window. Unlike
     * {@link #currentMenu(UUID)}, which reads a viewer's current top inventory, this inspects a specific inventory the
     * caller already holds (an {@code InventoryCloseEvent}'s inventory), so a feature can recognise one of its own
     * spec menus closing (to reopen it, say) through the facade, without reaching for the engine-internal
     * {@link MenuHolder} itself. A pure holder read, safe on any thread.
     */
    public Optional<String> menuIdOf(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return inventory.getHolder() instanceof MenuHolder menu ? Optional.of(menu.specId()) : Optional.empty();
    }

    /**
     * Redraw in place the menu {@code viewer} has open, when it is the one registered under {@code specId}: the seam a
     * feature whose window shows live state reaches for when that state changes (the far side of a trade staking an
     * item, say). It hops to the viewer's own entity thread and does nothing at all when they have since closed the
     * window or moved to another menu, so a stale update can never paint over an unrelated screen. The redraw reuses
     * the open window: no second {@code openInventory}, no new holder, and a content region the viewer physically fills
     * is left untouched.
     */
    public void redraw(Player viewer, String specId) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        scheduler.entity(viewer, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            openWindow(viewer, specId)
                    .map(Inventory::getHolder)
                    .filter(MenuHolder.class::isInstance)
                    .map(MenuHolder.class::cast)
                    .ifPresent(this::redrawWithFreshLists);
        });
    }

    /**
     * Reads the window's list sources again off the tick thread and draws it with the rows they return. A list is how a
     * window shows state that changes, so a redraw that painted the rows it opened with showed the change everywhere
     * but there. A window with no list is drawn at once.
     */
    private void redrawWithFreshLists(MenuHolder holder) {
        MenuSpec spec = holder.spec();
        if (spec.items().values().stream().noneMatch(item -> item.list().isPresent())) {
            // Nothing to read again, so nothing to wait for: the far side of a trade sees a stake at once.
            reRender(holder);
            return;
        }
        MenuContext ctx = holder.ctx();
        Player viewer = ctx.viewer();
        scheduler.async(() -> {
            ResolvedLists resolved = resolveLists(spec, ctx);
            scheduler.entity(viewer, () -> {
                // The draw itself skips a window the viewer has left since; these rows are only its rows.
                holder.setResolvedLists(resolved.rows());
                attachPagedViews(holder, holder.ctx(), resolved.paged());
                reRender(holder);
            });
        });
    }

    /**
     * The live window {@code viewer} has open when it is the menu registered under {@code specId}, else empty: how a
     * feature reads or clears the slots of its own {@code content {}} region without keeping a window reference of its
     * own. It is a plain read of the viewer's open inventory, so it must be called on their entity thread (where
     * touching a live inventory is legal), which is where every content-region callback already runs.
     */
    public Optional<Inventory> openWindow(Player viewer, String specId) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        if (!viewer.isOnline()) {
            return Optional.empty();
        }
        // A player looking at no window at all has nothing to read here, so the absent case is answered rather than
        // dereferenced.
        Inventory top = viewer.getOpenInventory().getTopInventory();
        if (top == null) {
            return Optional.empty();
        }
        return top.getHolder() instanceof MenuHolder holder && holder.specId().equals(specId)
                ? Optional.of(top)
                : Optional.empty();
    }

    /** Read the open menu's id, its 1-based page (the context page is 0-based), row count and typed arguments. */
    private static OpenMenuInfo openMenuInfo(MenuHolder holder) {
        return new OpenMenuInfo(
                holder.specId(),
                holder.ctx().page() + 1,
                holder.spec().rows(),
                holder.ctx().arguments());
    }

    /**
     * The id of the most-recently-opened menu in {@code viewer}'s history (the {@code /menu last} target) which
     * persists after that menu closes, unlike {@link #currentMenu}. Read from the thread-safe {@link LastMenu}, so it
     * needs no Bukkit read and answers the same on any thread. Empty when the engine was wired without a history
     * tracker (every list/spec-only fixture) or the viewer has opened no custom menu yet.
     */
    public Optional<String> lastMenuId(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            return Optional.empty();
        }
        return lastMenu.get(viewer).map(LastMenu.LastOpen::menuId);
    }

    /**
     * Open a typed property editor for {@code viewer} editing {@code subject}, as a holder-backed engine menu. It
     * builds the same {@link MenuHolder} every other menu uses (recognised and torn down by the one listener and one
     * {@code closeMenu}) but tags it with an {@link EditorState} so the listener routes its clicks through the editor's
     * property/button slots rather than a spec's. The window is shown on the viewer's entity thread, where touching the
     * live inventory is legal; unlike a list menu it queries no list source, so there is no off-thread resolve step. An
     * engine wired without an editor renderer cannot open an editor: that is a wiring error, so it fails loudly here.
     */
    public void openEditor(Player viewer, EditorSpec spec, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        scheduler.entity(viewer, () -> openEditorResolved(viewer, spec, subject));
    }

    /** On the viewer's entity thread: build the editor holder + window, render the editor, show it. No refresh. */
    private void openEditorResolved(Player viewer, EditorSpec spec, @Nullable Object subject) {
        if (!viewer.isOnline()) {
            return;
        }
        // A Bedrock viewer gets a native form here for the same reason they get one at every other open: a chest is
        // a window they cannot use properly. An editor has no chest-only escape and needs none, because it is a
        // list of settings and a form is a list of buttons: the two shapes match exactly.
        if (bedrock.isBedrock(viewer.getUniqueId())) {
            sendEditorForm(viewer, spec, subject);
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        MenuHolder holder = new MenuHolder(editorSpecId(spec), editorMenuSpec(spec), ctx);
        EditorState state = new EditorState(
                spec, subject, new EditorState.Clicks(requireEditorRenderer(), selectorOpener, confirmOpener));
        holder.attachEditor(state);
        Inventory inv = Bukkit.createInventory(
                holder,
                spec.layout().rows() * 9,
                MenuTitles.lay(
                        spec.title(viewer, subject), requireEditorRenderer().titleAlignment()));
        holder.attach(inv);
        requireEditorRenderer().populate(inv, spec, state, viewer);
        viewer.openInventory(inv);
    }

    /**
     * Re-render an open editor in place: the {@code reopen} target a property's click hook runs after its setter. It
     * hops to the viewer's entity thread, confirms the live top inventory is still this holder's editor window, clears
     * the editor's slot routing, and repaints the same inventory from the live subject so the changed value shows. No
     * second {@code openInventory} and no new holder: the window the viewer is looking at is reused, so the one
     * listener and one teardown keep owning it.
     */
    public void reRenderEditor(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        EditorRefresh.reRender(holder, requireEditorRenderer(), scheduler);
    }

    /**
     * Open a paginated entity list for {@code viewer} as a holder-backed engine menu. It builds the same {@link
     * MenuHolder} every other menu uses (recognised and torn down by the one listener and one {@code closeMenu}) but
     * tags it with a {@link ListViewState} so the listener routes its clicks through the list's
     * entity/nav/create/action slots rather than a spec's. The window is shown on the viewer's entity thread, where
     * touching the live inventory is legal; the entity supplier was already resolved off-thread by the caller, so there
     * is no off-thread resolve step here and the imperative icon renderer reads only the snapshot. A page flip re-
     * paginates the same holder (the listener's list branch), so a list arms no refresh timer and stays leak-balanced.
     */
    public void openList(Player viewer, EntityListSpec spec) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        scheduler.entity(viewer, () -> openListResolved(viewer, spec));
    }

    /** On the viewer's entity thread: build the list holder + window, render page zero, show it. No refresh. */
    private void openListResolved(Player viewer, EntityListSpec spec) {
        if (!viewer.isOnline()) {
            return;
        }
        // The same redirect the editor and the confirm take. A list of entities is already a list of buttons, so a
        // form carries the whole of it: every entity, the create button and the action button, with no paging
        // because a form scrolls on its own.
        if (bedrock.isBedrock(viewer.getUniqueId())) {
            sendListForm(viewer, spec);
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("list:" + spec.getClass().getSimpleName(), listMenuSpec(spec), ctx);
        ListViewState state = new ListViewState(spec);
        holder.attachListView(state);
        Inventory inv = Bukkit.createInventory(
                holder, spec.rows() * 9, MenuTitles.lay(spec.title(), renderer.titleAlignment()));
        holder.attach(inv);
        int clamped = listViewRenderer.populate(inv, spec, state, viewer, 0);
        holder.setCtx(ctx.withPage(clamped));
        viewer.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a list holder carries: the row count, refresh off, no items: clicks ride state. */
    private static MenuSpec listMenuSpec(EntityListSpec spec) {
        return new MenuSpec("", spec.rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open a two-button confirm window for {@code viewer}, laid out by the {@link ConfirmLook} the host set on the
     * builder, with its buttons named from the catalogue under {@link MenuKeys#CONFIRM_YES} and {@link
     * MenuKeys#CONFIRM_NO}. It builds the same {@link MenuHolder} every other menu uses, so the one listener routes its clicks and the one
     * {@code closeMenu} tears it down: clicking the yes button runs {@code onYes} exactly once, clicking no runs {@code
     * onNo} exactly once, and either click closes the window first. Closing the window (or quitting) without a click
     * runs neither. The window is shown on the viewer's entity thread, where touching the live inventory is legal; the
     * supplied runnables run on that same thread when their button is clicked, mirroring the editor and spec-action
     * click hops. The {@code title} is a {@link Component} the caller already resolved from a {@code String}, so the
     * window carries no inline user-facing literal.
     */
    public void confirm(Player viewer, Component title, Runnable onYes, Runnable onNo) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(onYes, "onYes");
        Objects.requireNonNull(onNo, "onNo");
        scheduler.entity(viewer, () -> openConfirmResolved(viewer, title, onYes, onNo));
    }

    /** On the viewer's entity thread: build the confirm holder + window, paint the two buttons, show it. No refresh. */
    private void openConfirmResolved(Player viewer, Component title, Runnable onYes, Runnable onNo) {
        if (!viewer.isOnline()) {
            return;
        }
        if (bedrock.isBedrock(viewer.getUniqueId())) {
            sendConfirmModal(viewer, title, onYes, onNo);
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        ConfirmLook look = confirmLook;
        MenuHolder holder = new MenuHolder("confirm", confirmMenuSpec(look), ctx);
        holder.attachConfirm(new ConfirmState(look.yesSlot(), look.noSlot(), onYes, onNo));
        Inventory inv =
                Bukkit.createInventory(holder, look.rows() * 9, MenuTitles.lay(title, renderer.titleAlignment()));
        holder.attach(inv);
        confirmRenderer.populate(
                inv,
                look,
                renderer.message(viewer, MenuKeys.CONFIRM_YES),
                renderer.message(viewer, MenuKeys.CONFIRM_NO));
        viewer.openInventory(inv);
    }

    /**
     * The Bedrock render of the confirm window: a native ModalForm (the caller's title plus a yes/no button pair)
     * instead of the confirm chest, with the same two words the chest names its buttons with, from the shared {@link
     * MenuKeys} catalog in the viewer's locale. A confirm is always safe to render as a form (it
     * is a plain two-choice prompt, not an item-display menu), so this redirect is unconditional for a Bedrock viewer
     * and never gated on {@code chestOnly}. Cumulus fires the response off the main thread, so each choice is wrapped
     * in a hop back to the viewer's entity thread before {@code onYes}/{@code onNo} runs.
     */
    private void sendConfirmModal(Player viewer, Component title, Runnable onYes, Runnable onNo) {
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);
        String yes = renderer.plainMessage(viewer, MenuKeys.CONFIRM_YES);
        String no = renderer.plainMessage(viewer, MenuKeys.CONFIRM_NO);
        bedrockScreen.sendModalForm(
                viewer,
                plainTitle,
                null,
                yes,
                no,
                () -> scheduler.entity(viewer, onYes),
                () -> scheduler.entity(viewer, onNo));
    }

    /**
     * The Bedrock render of an editor: one form button per property, then back, then delete.
     *
     * <p>A property tile and a form button are the same thing said twice. The chest paints an icon whose tooltip
     * holds the setting's name and the value it carries now; the form shows a button labelled with the same two,
     * the name on the first line and the value under it, and the same icon beside it.
     *
     * <p>A tap runs the property's own click, which is the click the chest runs on a left click. Nothing here
     * reaches the right click or the shift click: a form sends one tap and no modifier, and an editor property
     * that could only be changed by a modifier would be a setting half this server's players cannot change. Every
     * property type the engine ships opens its prompt on a plain click.
     *
     * <p>The reopen a property gets back re-sends this form rather than repainting a window, because there is no
     * window: the properties are read from the subject again, so the value the operator just wrote is the value
     * the next form shows.
     */
    private void sendEditorForm(Player viewer, EditorSpec spec, @Nullable Object subject) {
        List<BedrockButton> buttons = new ArrayList<>();
        List<Runnable> handlers = new ArrayList<>();
        Runnable reopen = () -> scheduler.entity(viewer, () -> {
            if (viewer.isOnline()) {
                sendEditorForm(viewer, spec, subject);
            }
        });
        for (EditableProperty property : spec.propertiesFor(subject)) {
            buttons.add(new BedrockButton(
                    editorButtonText(viewer, property),
                    BedrockIcons.forMaterialSpec(property.icon().name(), viewer.getUniqueId())));
            handlers.add(() -> scheduler.entity(viewer, () -> {
                if (viewer.isOnline()) {
                    property.onClick(new PropertyClick(viewer, false, false, reopen, selectorOpener, confirmOpener));
                }
            }));
        }
        buttons.add(new BedrockButton(renderer.plainMessage(viewer, spec.backName()), null));
        handlers.add(() -> scheduler.entity(viewer, () -> {
            if (viewer.isOnline()) {
                spec.onBack().accept(viewer);
            }
        }));
        spec.deleteName().ifPresent(name -> {
            buttons.add(new BedrockButton(renderer.plainMessage(viewer, name), null));
            handlers.add(() -> scheduler.entity(viewer, () -> {
                if (viewer.isOnline()) {
                    askThenDelete(viewer, spec, subject, reopen);
                }
            }));
        });
        bedrockScreen.sendSimpleForm(
                viewer,
                PlainTextComponentSerializer.plainText().serialize(spec.title(viewer, subject)),
                null,
                buttons,
                index -> {
                    if (index >= 0 && index < handlers.size()) {
                        handlers.get(index).run();
                    }
                });
    }

    /**
     * The delete button of the editor form: a modal first when the spec carries a confirm title, the handler
     * straight when it does not.
     *
     * <p>A spec with no title is a caller that gates inside its own handler, and on this platform that gate is a
     * modal too, because {@link #confirm} redirects a Bedrock viewer to one. What this closes is the other case:
     * the chest editor asks before it deletes, and the form used to delete on the first tap. The same button on
     * the same editor cannot be one question on Java and none on Bedrock.
     */
    private void askThenDelete(Player viewer, EditorSpec spec, @Nullable Object subject, Runnable reopen) {
        Runnable delete = () -> spec.onDelete().ifPresent(handler -> handler.accept(viewer, subject));
        String confirmTitle = spec.deleteConfirmTitle().orElse(null);
        if (confirmTitle == null) {
            delete.run();
            return;
        }
        sendConfirmModal(viewer, Component.text(renderer.plainMessage(viewer, confirmTitle)), delete, reopen);
    }

    /** One property as a button label: the setting's name, and the value it holds now on the line under it. */
    private String editorButtonText(Player viewer, EditableProperty property) {
        String name = renderer.plainMessage(viewer, property.label());
        String value = property.drawnValue(viewer)
                .map(drawn -> PlainTextComponentSerializer.plainText().serialize(drawn))
                .orElseGet(() -> property.valueLore(viewer));
        return value.isBlank() ? name : name + "\n" + value;
    }

    /**
     * The Bedrock render of an entity list: one form button per entity, then create, then action, then the extras.
     *
     * <p>No paging. A chest holds as many entities as it has content slots and needs a previous and a next button
     * to reach the rest; a Cumulus form scrolls, so the whole list goes on it and the two nav buttons have nothing
     * to do. A tap runs the same {@code onSelect} the chest icon runs.
     *
     * <p>The label of an entity is the display name of the icon the chest would have drawn for it, so a list reads
     * the same on both clients and a caller writes its naming once.
     */
    private void sendListForm(Player viewer, EntityListSpec spec) {
        List<BedrockButton> buttons = new ArrayList<>();
        List<Runnable> handlers = new ArrayList<>();
        for (Object entity : spec.entities()) {
            buttons.add(new BedrockButton(iconText(viewer, spec, entity), null));
            handlers.add(() -> scheduler.entity(viewer, () -> {
                if (viewer.isOnline()) {
                    spec.onSelect().accept(viewer, entity);
                }
            }));
        }
        spec.createName()
                .ifPresent(name -> spec.onCreate().ifPresent(create -> {
                    buttons.add(new BedrockButton(
                            PlainTextComponentSerializer.plainText().serialize(name), null));
                    handlers.add(() -> scheduler.entity(viewer, () -> {
                        if (viewer.isOnline()) {
                            create.accept(viewer);
                        }
                    }));
                }));
        spec.actionName()
                .ifPresent(name -> spec.onAction().ifPresent(action -> {
                    buttons.add(new BedrockButton(
                            PlainTextComponentSerializer.plainText().serialize(name), null));
                    handlers.add(() -> scheduler.entity(viewer, () -> {
                        if (viewer.isOnline()) {
                            action.accept(viewer);
                        }
                    }));
                }));
        bedrockScreen.sendSimpleForm(
                viewer, PlainTextComponentSerializer.plainText().serialize(spec.title()), null, buttons, index -> {
                    if (index >= 0 && index < handlers.size()) {
                        handlers.get(index).run();
                    }
                });
    }

    /** What the chest would call this entity: the display name of the icon the list renders for it. */
    private static String iconText(Player viewer, EntityListSpec spec, Object entity) {
        return iconName(spec.iconRenderer().apply(viewer, entity));
    }

    /** An already-built icon's display name as plain text, or its material when it carries none. */
    private static String iconName(ItemStack icon) {
        ItemMeta meta = icon.getItemMeta();
        Component name = meta == null ? null : meta.displayName();
        return name == null
                ? icon.getType().name()
                : PlainTextComponentSerializer.plainText().serialize(name);
    }

    /** The minimal {@link MenuSpec} a confirm holder carries: the look's rows, refresh off, no items: clicks ride state. */
    private static MenuSpec confirmMenuSpec(ConfirmLook look) {
        return new MenuSpec("", look.rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The opener a property hands its picker to: opening a selector through it shows a {@link MenuHolder} child window
     * the one listener routes and the one {@code closeMenu} tears down. Threaded into the editor {@code PropertyClick}
     * so an {@link com.uxplima.uxmlib.menu.property.EnumProperty} (and, as they migrate,
     * the list/colour pickers) opens an engine child rather than a uxmLib {@code SimpleGui} on the engine runtime.
     */
    public SelectorOpener selectorOpener() {
        return selectorOpener;
    }

    /**
     * The opener a property hands a destructive step to: it gates the step behind a {@link MenuHolder} confirm child
     * the one listener routes and the one {@code closeMenu} tears down. Threaded into the editor {@code PropertyClick}
     * alongside {@link #selectorOpener()} so a {@link com.uxplima.uxmlib.menu.property.ListProperty}'s
     * remove gesture opens an engine confirm on the engine
     * runtime.
     */
    public ConfirmOpener confirmOpener() {
        return confirmOpener;
    }

    /**
     * Open a selector child window for {@code viewer}: a flat picker of option buttons, the engine's replacement for a
     * property's uxmLib {@code SimpleGui} selector. It builds the same {@link MenuHolder} every other menu uses, so the
     * one listener routes its clicks and the one {@code closeMenu} tears it down: clicking an option button runs its
     * choose action exactly once, and either that or closing the window (or quitting) ends the picker. The window is
     * shown on the viewer's entity thread, where touching the live inventory is legal; each button's choose action runs
     * on that same thread when clicked, mirroring the editor and confirm hops. The {@code title} is a {@link Component}
     * the caller resolved from a {@code String}, so the window carries no inline literal.
     */
    public void openSelector(Player viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(buttons, "buttons");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        List<SelectorButton> copy = List.copyOf(buttons);
        scheduler.entity(viewer, () -> openSelectorResolved(viewer, title, rows, filler, copy));
    }

    /** On the viewer's entity thread: build the selector holder + window, paint the option buttons, show it. */
    private void openSelectorResolved(
            Player viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
        if (!viewer.isOnline()) {
            return;
        }
        // A selector of plain choices is a list of buttons, so a Bedrock viewer gets it as a form like every other
        // open. One holding a gesture-aware button is not: that button is four verbs on one square, a form sends one
        // tap and no modifier, and turning it into a form would take three of the four away. Such a selector stays a
        // chest, where Geyser can still send a right click and a shift click.
        if (bedrock.isBedrock(viewer.getUniqueId()) && buttons.stream().noneMatch(SelectorButton::gestureAware)) {
            sendSelectorForm(viewer, title, buttons);
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("selector", selectorMenuSpec(rows), ctx);
        Map<Integer, ChildClickHandler> choices = new HashMap<>();
        for (SelectorButton button : buttons) {
            choices.put(button.slot(), button.onClick());
        }
        holder.attachSelector(new SelectorState(choices));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, MenuTitles.lay(title, renderer.titleAlignment()));
        holder.attach(inv);
        selectorRenderer.populate(inv, filler, buttons);
        viewer.openInventory(inv);
    }

    /**
     * The Bedrock render of a selector: one form button per choice, in slot order.
     *
     * <p>The label is the display name of the icon the chest would have drawn, so a rarity reads the same on both
     * clients and the caller writes its naming once. A tap runs the choice's handler with neither the right click
     * nor the shift flag, which is the gesture every button on a form-eligible selector already ignores.
     */
    private void sendSelectorForm(Player viewer, Component title, List<SelectorButton> buttons) {
        List<SelectorButton> inSlotOrder = new ArrayList<>(buttons);
        inSlotOrder.sort(java.util.Comparator.comparingInt(SelectorButton::slot));
        List<BedrockButton> shown = new ArrayList<>();
        List<Runnable> handlers = new ArrayList<>();
        for (SelectorButton button : inSlotOrder) {
            shown.add(new BedrockButton(iconName(button.icon()), null));
            handlers.add(() -> scheduler.entity(viewer, () -> {
                if (viewer.isOnline()) {
                    button.onClick().onClick(false, false);
                }
            }));
        }
        bedrockScreen.sendSimpleForm(
                viewer, PlainTextComponentSerializer.plainText().serialize(title), null, shown, index -> {
                    if (index >= 0 && index < handlers.size()) {
                        handlers.get(index).run();
                    }
                });
    }

    /**
     * The minimal {@link MenuSpec} a selector holder carries: the row count, refresh off, no items: clicks ride state.
     */
    private static MenuSpec selectorMenuSpec(int rows) {
        return new MenuSpec("", rows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open a slot-grid canvas for {@code viewer}: the engine's visual editor window, opened by the custom-menus grid
     * editor. It builds the same {@link MenuHolder} every other menu uses (recognised and torn down by the one listener
     * and one {@code closeMenu}) but tags it with a {@link GridViewState} so the listener routes its clicks through the
     * canvas's content/nav/control slots rather than a spec's. The window is shown on the viewer's entity thread, where
     * touching the live inventory is legal; the {@code spec}'s content supplier is read imperatively on that thread
     * over the caller's already-loaded edit model, so there is no off-thread resolve step. Unlike a list a grid re-
     * reads its content on every draw (a place / move / clear then a {@link GridView#reRender}), so it arms no refresh
     * timer and stays leak-balanced.
     */
    public void openGrid(Player viewer, GridSpec spec, GridHandlers handlers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(handlers, "handlers");
        scheduler.entity(viewer, () -> openGridResolved(viewer, spec, handlers));
    }

    /**
     * On the viewer's entity thread: build the grid holder + window, render page zero, show it. No refresh.
     *
     * <p>The one surface with no Bedrock branch, and it is the same reason a menu writes {@code chest-only}: a grid
     * is a place a player puts items into and takes them out of, and a Cumulus form has no shape for that. Every
     * other open in this engine redirects a Floodgate viewer to a form. This one cannot, so it does not pretend to.
     */
    private void openGridResolved(Player viewer, GridSpec spec, GridHandlers handlers) {
        if (!viewer.isOnline()) {
            return;
        }
        int windowRows = GridRenderer.windowRows(spec.menuRows());
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("grid:" + spec.menuRows(), gridMenuSpec(windowRows), ctx);
        GridViewState state = new GridViewState(spec, handlers);
        holder.attachGridView(state);
        Inventory inv =
                Bukkit.createInventory(holder, windowRows * 9, MenuTitles.lay(spec.title(), renderer.titleAlignment()));
        holder.attach(inv);
        // The grid renderer is built at the use site rather than in the constructor, so an engine wired with a stub or
        // mocked MenuRenderer (many spec-only test fixtures) never dereferences its item renderer unless a grid opens.
        int clamped = new GridRenderer(renderer.itemRenderer()).populate(inv, spec, state, viewer, 0);
        holder.setCtx(ctx.withPage(clamped));
        viewer.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a grid holder carries: the window's row count, refresh off: clicks ride state. */
    private static MenuSpec gridMenuSpec(int windowRows) {
        return new MenuSpec("", windowRows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open {@code spec} for {@code viewer} as a live preview: render an in-memory working copy exactly as a player
     * would see it, through the real {@link MenuRenderer}, without registering the spec. The menu editor uses this so
     * an operator can look over their unsaved edits before committing them to disk: the working copy is frozen into an
     * immutable {@link MenuSpec} and handed here. It builds the same {@link MenuHolder} every other menu uses (so the
     * one listener routes its clicks and the one {@code closeMenu} tears it down) and attaches {@code onClose}, the
     * seam the close path runs once to step the operator back to the grid editor when the preview closes.
     *
     * <p>A preview is deliberately not a full open: it does not gate on the spec's open-requirement, run its open-
     * actions, record it in the viewer's {@code /menu last} history, paint the bottom inventory, or arm a refresh
     * timer: those are the side effects of really opening a menu, and a preview is a look, not an open. Its clicks
     * still run the spec's own click actions (that is what "as a player sees it" means), so a preview of a shop can be
     * clicked through live. Its list sources are resolved off the tick thread like any open, then the window is shown
     * on the viewer's entity thread.
     */
    public void openPreview(Player viewer, MenuSpec spec, Runnable onClose) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(onClose, "onClose");
        MenuContext ctx = MenuContext.of(viewer, null, 0).withLocalPlaceholders(spec.placeholders());
        scheduler.async(() -> {
            ResolvedLists resolved = resolveLists(spec, ctx);
            scheduler.entity(viewer, () -> openPreviewResolved(viewer, spec, ctx, resolved, onClose));
        });
    }

    /** On the viewer's entity thread: build the preview holder + window, render it, attach the back hook, show it. */
    private void openPreviewResolved(
            Player viewer, MenuSpec spec, MenuContext ctx, ResolvedLists resolved, Runnable onClose) {
        if (!viewer.isOnline()) {
            return;
        }
        MenuHolder holder = new MenuHolder("preview:" + spec.rows(), spec, ctx);
        holder.setResolvedLists(resolved.rows());
        MenuContext renderCtx = attachPagedViews(holder, ctx, resolved.paged());
        holder.attachCloseHook(onClose);
        Inventory inv = createWindow(holder, spec, renderer.title(spec, renderCtx));
        holder.attach(inv);
        renderer.populate(inv, spec, renderCtx, holder::recordSlot, holder.resolvedLists());
        viewer.openInventory(inv);
    }

    private EditorRenderer requireEditorRenderer() {
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        return editorRenderer;
    }

    /** A stable holder id for an editor open; editors are code-built and not registered, so the type name suffices. */
    private static String editorSpecId(EditorSpec spec) {
        return "editor:" + spec.getClass().getSimpleName();
    }

    /**
     * A minimal {@link MenuSpec} the editor holder carries so {@link MenuRefresh} and the holder's accessors have
     * something coherent to read: the editor's row count, refresh disabled (an editor is repainted on a click, never
     * on a timer), and no items (an editor's buttons live on its {@link EditorState}, not in a spec). The editor
     * render path never reads this spec's items, so an empty item map is exactly right.
     */
    private static MenuSpec editorMenuSpec(EditorSpec spec) {
        return new MenuSpec(
                "", spec.layout().rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The rows one open resolved for every list the spec names, keyed by source id (a paged source's rows and an
     * in-memory source's whole corpus are stored the same way, so the render path does not care which kind produced
     * them), plus the paging metadata each paged source additionally reported. That metadata is applied to the holder
     * on the viewer's entity thread by {@link #attachPagedViews}; it never rides back onto a tick thread.
     */
    private record ResolvedLists(Map<String, List<?>> rows, Map<String, PagedListMeta> paged) {}

    /** What a paged source reported beyond its rows: the corpus total, and the page size and sorts the request used. */
    private record PagedListMeta(long total, int size, List<String> sorts) {}

    /**
     * Resolve every list source the spec names. Runs off the viewer's region thread because a source may read a
     * database; an unregistered source resolves to an empty list so a wiring gap renders an empty grid rather than
     * failing the open. This is the only place a source is queried for one open. A paged source is asked for a single
     * default page (page zero, the spec's first declared sort, no filters) because on first open there is no holder yet
     * to carry the viewer's page; that {@link PageRequest} is a plain immutable value, so building and using it off the
     * entity thread touches no per-viewer state.
     */
    private ResolvedLists resolveLists(MenuSpec spec, MenuContext ctx) {
        Map<String, List<?>> rows = new HashMap<>();
        Map<String, PagedListMeta> paged = new HashMap<>();
        for (MenuItemSpec item : spec.items().values()) {
            item.list().ifPresent(listSpec -> resolveListSource(item, listSpec, ctx, rows, paged));
        }
        return new ResolvedLists(rows, paged);
    }

    /** Resolve one list-backed item's source: the in-memory registry first, then the paged one, else an empty list. */
    private void resolveListSource(
            MenuItemSpec item,
            ListSpec listSpec,
            MenuContext ctx,
            Map<String, List<?>> rows,
            Map<String, PagedListMeta> paged) {
        String sourceId = listSpec.source().id();
        Optional<Function<MenuContext, List<?>>> plain = lists.get(sourceId);
        if (plain.isPresent()) {
            rows.put(sourceId, plain.get().apply(ctx));
            return;
        }
        Optional<BiFunction<MenuContext, PageRequest, PagedResult<?>>> pagedSource = pagedLists.get(sourceId);
        if (pagedSource.isEmpty()) {
            rows.put(sourceId, List.of());
            return;
        }
        resolvePagedSource(item, listSpec, sourceId, pagedSource.get(), ctx, rows, paged);
    }

    /** Ask a paged source for the default page, store its rows (pinned first) under the id, and note its reported total. */
    private void resolvePagedSource(
            MenuItemSpec item,
            ListSpec listSpec,
            String sourceId,
            BiFunction<MenuContext, PageRequest, PagedResult<?>> source,
            MenuContext ctx,
            Map<String, List<?>> rows,
            Map<String, PagedListMeta> paged) {
        int size = pageSize(item, listSpec);
        String sort = listSpec.sorts().isEmpty() ? "" : listSpec.sorts().get(0);
        PagedResult<?> result = source.apply(ctx, new PageRequest(0, size, sort, Map.of()));
        rows.put(sourceId, PagedListRows.combine(sourceId, size, result, LOG));
        paged.put(sourceId, new PagedListMeta(result.totalCount(), size, listSpec.sorts()));
    }

    /** The page size the default request uses: the spec's explicit page-size, or the item's slot count when it is zero. */
    private static int pageSize(MenuItemSpec item, ListSpec listSpec) {
        return listSpec.pageSize() != 0
                ? listSpec.pageSize()
                : item.slots().slots().size();
    }

    /**
     * On the viewer's entity thread: record each paged list's reported total on its {@link ListQueryState} (the single
     * place per open that state is written) and return a render context carrying an immutable {@link PagedListView} per
     * list so the renderer knows which lists are already paged and what page count their indicator reads. Returns the
     * context unchanged when the open queried no paged source, so an in-memory-only open is untouched.
     */
    private MenuContext attachPagedViews(MenuHolder holder, MenuContext ctx, Map<String, PagedListMeta> paged) {
        if (paged.isEmpty()) {
            return ctx;
        }
        Map<String, PagedListView> views = new HashMap<>();
        paged.forEach((listId, meta) -> {
            ListQueryState state = holder.queryState(listId, meta.sorts());
            state.recordTotal(meta.total());
            views.put(listId, new PagedListView(state.page(), meta.total(), meta.size()));
        });
        MenuContext renderCtx = ctx.withPagedViews(views);
        holder.setCtx(renderCtx);
        return renderCtx;
    }

    /**
     * Fire the public, cancellable {@link MenuOpenEvent} and report whether a listener vetoed the open. Called at the
     * very top of the one open choke-point every open (a fresh open, a {@code back} step, a reopen-last) funnels
     * through, before the Bedrock form branches and the chest build alike, so a cancelled open shows the viewer neither
     * a native form nor a chest. It fires on the viewer's own region thread the open already runs on.
     */
    private static boolean openVetoed(Player live, String specId, int page) {
        MenuOpenEvent event = new MenuOpenEvent(live, specId, page);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    /** On the viewer's entity thread: build the holder-backed window, cache the lists, render, show, arm refresh. */
    private void openResolved(
            Player viewer,
            String specId,
            MenuSpec spec,
            @Nullable Object subject,
            ResolvedLists resolved,
            int page,
            Map<String, String> arguments,
            Map<String, Object> passthrough,
            boolean record) {
        if (!viewer.isOnline()) {
            return;
        }
        if (openVetoed(viewer, specId, page)) {
            return;
        }
        // Attach the spec's own placeholders {} block so the renderer resolves its %name% tokens local-first, and
        // whatever the host passed through; the holder carries this ctx, so a refresh or re-render reads it back and
        // keeps both through the redraw.
        MenuContext ctx = MenuContext.of(viewer, subject, page, arguments)
                .withLocalPlaceholders(spec.placeholders())
                .withPassthrough(passthrough);
        if (!gateOpen(spec, ctx)) {
            return;
        }
        // A Bedrock viewer gets a native Cumulus form instead of the chest, unless the menu keeps the chest
        // (chest-only, a content region or a bottom-inventory canvas: items a form cannot show). An explicit per-menu
        // bedrock {} block wins first: it defines a native CustomForm (the dropdown/slider/toggle/multi-input widgets
        // the automatic SimpleForm degradation cannot express) so a menu that declares one sends that form rather than
        // the degraded button list. Absent a block, the automatic degradation is unchanged. A form is an alternative
        // render at the open choke-point, not a second window, so it builds no holder and arms no refresh; but it is
        // still an open, so it records into the back history and fires the menu's open-actions the same way the chest
        // path does: otherwise a back from a form would have no history to step to and a menu's open-actions would
        // never fire for a Bedrock viewer. A Java viewer (isBedrock false) falls straight through to the chest path
        // unchanged.
        if (bedrock.isBedrock(viewer.getUniqueId())
                && !spec.keepsTheChest()
                && spec.bedrock().isPresent()) {
            forms.sendCustomForm(viewer, spec, ctx);
            afterBedrockOpen(spec, viewer, ctx, viewer, specId, subject, page, arguments, record);
            return;
        }
        // The resolved list cache is threaded through so a list-backed menu's entries page as form buttons.
        if (bedrock.isBedrock(viewer.getUniqueId()) && !spec.keepsTheChest()) {
            forms.sendSimpleForm(viewer, spec, ctx, resolved.rows());
            afterBedrockOpen(spec, viewer, ctx, viewer, specId, subject, page, arguments, record);
            return;
        }
        MenuHolder holder = new MenuHolder(specId, spec, ctx);
        holder.setResolvedLists(resolved.rows());
        // Record each paged list's reported total on the holder's query state and take an immutable render context that
        // carries a view of each, all on this entity thread: so the mutable state is never touched off it.
        MenuContext renderCtx = attachPagedViews(holder, ctx, resolved.paged());
        Inventory inv = createWindow(holder, spec, renderer.title(spec, renderCtx));
        holder.attach(inv);
        renderer.populate(inv, spec, renderCtx, holder::recordSlot, holder.resolvedLists());
        if (spec.bottomInventory()) {
            paintBottom(holder, spec, renderCtx, viewer);
        }
        viewer.openInventory(inv);
        if (record) {
            rememberLastOpen(viewer, specId, subject, page, arguments);
        }
        runOpenActions(spec, viewer, ctx);
        MenuRefresh.start(holder, scheduler, () -> reRender(holder));
    }

    /**
     * The bookkeeping a Bedrock form open shares with the chest path once the form is on the viewer's screen: record
     * this open into the viewer's back history (subject permitting: the same rule the chest path applies) so a {@code
     * back} from the form can step to it, and fire the menu's open-actions. A form carries no window, so (unlike the
     * chest path) it builds no holder and arms no refresh; this runs only the two pieces that describe an open rather
     * than a window. A reopen (a {@code back} re-sending the previous form) passes {@code record} false, so stepping
     * back never re-stacks the form it just returned to.
     */
    private void afterBedrockOpen(
            MenuSpec spec,
            Player live,
            MenuContext ctx,
            Player viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            boolean record) {
        if (record) {
            rememberLastOpen(viewer, specId, subject, page, arguments);
        }
        runOpenActions(spec, live, ctx);
    }

    /**
     * Remember this open as the viewer's {@code /menu last} target: but only a subject-less one, a disk-loaded custom
     * menu. A feature menu carries a live domain subject (a warp, a home owner) that must never be reopened blind, and
     * it has its own command, so those are deliberately not recorded. An engine wired without a tracker (every
     * list/spec-only fixture) records nothing.
     */
    private void rememberLastOpen(
            Player viewer, String specId, @Nullable Object subject, int page, Map<String, String> arguments) {
        if (lastMenu != null && subject == null) {
            lastMenu.record(viewer.getUniqueId(), new LastMenu.LastOpen(specId, page, arguments));
        }
    }

    /**
     * Build the window a spec opens into: its declared non-chest {@link InventoryType} when it names one the server
     * accepts, else the default {@code rows}-based chest. A non-chest shape is best-effort (some types reject a custom
     * holder or title on some servers) so a thrown build is caught, logged once, and downgraded to the chest, meaning a
     * bad {@code inventory-type} never leaves the viewer with a blank or missing window.
     */
    private Inventory createWindow(MenuHolder holder, MenuSpec spec, Component raw) {
        Component title = MenuTitles.lay(raw, renderer.titleAlignment());
        Optional<InventoryType> type = spec.inventoryType().flatMap(Menus::resolveInventoryType);
        if (type.isEmpty()) {
            return Bukkit.createInventory(holder, spec.rows() * 9, title);
        }
        try {
            return Bukkit.createInventory(holder, type.get(), title);
        } catch (RuntimeException rejected) {
            LOG.warning("menu inventory type '" + spec.inventoryType().orElse("")
                    + "' could not be created, falling back to a chest: " + rejected.getMessage());
            return Bukkit.createInventory(holder, spec.rows() * 9, title);
        }
    }

    /**
     * Map an operator-friendly inventory-type token to the Bukkit {@link InventoryType} that shapes the window. {@code
     * chest}, a blank token, or any name not listed here resolves to empty, i.e. the default {@code rows}-based chest:
     * an unknown type is a soft miss, not a failure. A couple of obvious aliases are accepted so a spec author can
     * write the block name they know ({@code shulker}/{@code shulker_box}, {@code ender}/{@code ender_chest}, {@code
     * workbench}/{@code crafting}).
     */
    private static Optional<InventoryType> resolveInventoryType(String name) {
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "hopper" -> Optional.of(InventoryType.HOPPER);
            case "dropper" -> Optional.of(InventoryType.DROPPER);
            case "dispenser" -> Optional.of(InventoryType.DISPENSER);
            case "furnace" -> Optional.of(InventoryType.FURNACE);
            case "anvil" -> Optional.of(InventoryType.ANVIL);
            case "brewing", "brewing_stand" -> Optional.of(InventoryType.BREWING);
            case "beacon" -> Optional.of(InventoryType.BEACON);
            case "shulker", "shulker_box" -> Optional.of(InventoryType.SHULKER_BOX);
            case "barrel" -> Optional.of(InventoryType.BARREL);
            case "lectern" -> Optional.of(InventoryType.LECTERN);
            case "loom" -> Optional.of(InventoryType.LOOM);
            case "ender", "ender_chest", "enderchest" -> Optional.of(InventoryType.ENDER_CHEST);
            case "workbench", "crafting", "crafting_table" -> Optional.of(InventoryType.WORKBENCH);
            default -> Optional.empty();
        };
    }

    /**
     * Whether this menu may open for {@code viewer} given its {@code open-requirement}, evaluated by the shared
     * {@link #passes} gate so the open path and a command's own requirements read a token identically.
     */
    private boolean gateOpen(MenuSpec spec, MenuContext ctx) {
        return passes(ctx.viewer(), spec.openRequirement(), ctx.arguments());
    }

    /**
     * Whether every requirement in {@code requirements} tests true for {@code viewer}, with {@code arguments} bound.
     * The gate is open (the open proceeds, the command runs) when the engine was wired without a condition registry
     * (every list/spec-only test engine) or nothing is required. Otherwise every requirement ref is an AND gate: each
     * is resolved against the condition registry (the same registry-aware split the click path uses, so a valued token
     * like {@code has-money:100} reaches its handler with {@code value=100}), has its {@code %argument_<name>%} tokens
     * expanded from {@code arguments} (so a gate can read a typed command argument, for example {@code
     * expr:%argument_amount% > 0}), and must test true. An unregistered or false condition fails the gate closed, so a
     * wiring gap keeps the window shut rather than showing it.
     */
    public boolean passes(Player viewer, List<Ref> requirements, Map<String, String> arguments) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(arguments, "arguments");
        ConditionRegistry conditions = openConditionRegistry;
        if (conditions == null || requirements.isEmpty()) {
            return true;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0, arguments);
        for (Ref ref : requirements) {
            Ref eff = ref.resolve(conditions::has);
            boolean pass = conditions
                    .get(eff.id())
                    .map(p -> p.test(ctx, ActionArguments.resolve(eff.args(), ctx.arguments())))
                    .orElse(false);
            if (!pass) {
                return false;
            }
        }
        return true;
    }

    /**
     * Run the spec's {@code open-actions} in order, now that the window is open on the viewer's entity thread: where
     * touching the live inventory is legal. Skipped when the engine was wired without an action registry (a list/spec-
     * only test engine). Each ref is resolved against the
     * action registry (the same registry-aware split the click path takes) and dispatched through a {@link
     * MenuActionContext} carrying {@link ClickKind#LEFT} as the neutral kind (no gesture fired on an open) and the
     * four-argument, no-control constructor, so a {@code refresh} written as an open-action is a harmless no-op rather
     * than a null-control failure. An action's {@code %argument_<name>%} tokens are expanded from the arguments the
     * menu was opened with, matching the click and render paths. Open-actions fire simply here; the per-action delay
     * and chance modifiers a click action honours are a later concern.
     */
    private void runOpenActions(MenuSpec spec, Player live, MenuContext ctx) {
        ActionRegistry actions = openActionRegistry;
        if (actions == null) {
            return;
        }
        for (Ref ref : spec.openActions()) {
            Ref eff = ref.resolve(actions::has);
            actions.get(eff.id())
                    .ifPresent(handler -> handler.accept(new MenuActionContext(
                            ctx, live, ClickKind.LEFT, ActionArguments.resolve(eff.args(), ctx.arguments()))));
        }
    }

    /**
     * Run a list of action refs against {@code ctx} on the viewer's entity thread the caller already hopped onto,
     * through the very {@link ActionRegistry} the click listener resolves against (production hands both the same
     * {@code MenuBindings.actions()} instance). Each ref is split registry-aware, then its argument values have their
     * {@code %argument_<name>%} tokens and their menu-local {@code %name%} tokens expanded (the same two-channel
     * substitution the click path's dispatch applies) so a form tap or an on-submit action reaches the identical
     * handler with the identical arguments a chest click would. Skipped when the engine was wired without an action
     * registry (a list/spec-only test engine), matching {@link #runOpenActions}.
     */
    private void runActions(MenuContext ctx, List<Ref> refs) {
        runActions(ctx, refs, ClickKind.LEFT);
    }

    /** The same run, told which gesture asked for it, so a handler that reads the gesture reads the right one. */
    private void runActions(MenuContext ctx, List<Ref> refs, ClickKind kind) {
        ActionRegistry actions = openActionRegistry;
        if (actions == null) {
            return;
        }
        Player viewer = ctx.viewer();
        if (!viewer.isOnline()) {
            return;
        }
        for (Ref ref : refs) {
            Ref eff = ref.resolve(actions::has);
            Map<String, String> args = ActionArguments.resolveLocals(
                    ActionArguments.resolve(eff.args(), ctx.arguments()), ctx.localPlaceholders());
            java.util.Optional<java.util.function.Consumer<MenuActionContext>> handler = actions.get(eff.id());
            if (handler.isEmpty()) {
                warnUnknownAction(eff);
                continue;
            }
            handler.get().accept(new MenuActionContext(ctx, viewer, kind, args));
        }
    }

    /**
     * Read a menu's ids back against the registries, the first time it is opened, and say which of them
     * nobody answers.
     *
     * <p>An action nobody registered is a button that does nothing and the click path says so; a
     * condition nobody registered hides the tile it guards, which is the safe direction and the one no
     * click can ever report. Both are a word an operator typed, so both are read here in one pass and
     * named in one line per menu.
     *
     * <p>Once per spec, and at the first open rather than at registration: a plugin may register a spec
     * before the vocabulary it names, and a check at render would repeat several times a second.
     */
    private void warnUnknownRefsOnce(String specId, MenuSpec spec) {
        if (!refsChecked.add(specId)) {
            return;
        }
        ActionRegistry actions = openActionRegistry;
        ConditionRegistry conditions = openConditionRegistry;
        if (actions != null) {
            List<String> unknown = SpecRefs.unknownActions(spec, actions::has);
            if (!unknown.isEmpty()) {
                LOG.warning("menu " + specId + " names actions no plugin registered: " + unknown
                        + ". A click on one of those tiles does nothing; check the spelling.");
            }
        }
        if (conditions != null) {
            List<String> unknown = SpecRefs.unknownConditions(spec, conditions::has);
            if (!unknown.isEmpty()) {
                LOG.warning("menu " + specId + " names conditions no plugin registered: " + unknown
                        + ". A tile behind one of those is hidden from everybody; check the spelling.");
            }
        }
    }

    /**
     * Say that a click named an action nobody registered.
     *
     * <p>A menu is a file an operator edits and an action id is a word they type, so a typo was a
     * button that did nothing: the id was looked up, nothing was found, and the next ref ran. The
     * window opens, the tile draws, and the click is absent rather than broken, with nothing to read.
     * The grammar half of the engine has always said so, because a line it cannot parse throws.
     *
     * <p>The rest of the list still runs. One wrong id costs the operator that action and not the
     * three beside it, which is the same rule a moment runner follows for a line it cannot read.
     */
    static void warnUnknownAction(Ref ref) {
        LOG.warning("menu click names " + ref.id() + " and no action is registered under that id:"
                + " check the spelling against /uxmess actions or the plugin's own list");
    }

    /** Redraw an open menu in place on its viewer's thread, but only if that window is still this holder's. */
    private void reRender(MenuHolder holder) {
        scheduler.entity(holder.ctx().viewer(), () -> {
            Player p = holder.ctx().viewer();
            if (!p.isOnline()) {
                return;
            }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
                return;
            }
            holder.clearClickMap();
            renderer.populate(
                    holder.getInventory(),
                    holder.spec(),
                    holder.ctx(),
                    holder::recordSlot,
                    holder.resolvedLists(),
                    false);
            if (holder.spec().bottomInventory()) {
                // Re-paint the bottom too, but do not re-snapshot: the viewer's real items were captured on open and
                // are held on the holder until close; populateBottom clears and redraws only the menu tiles.
                renderer.populateBottom(
                        p.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
            }
        });
    }

    /**
     * The open-time half of a bottom-inventory menu: snapshot the viewer's real 36 bottom slots onto the holder, then
     * paint the menu's bottom items into them. The snapshot is what the close restores (and what a death drops in place
     * of the menu tiles), so it is taken before {@code populateBottom} clears and repaints the canvas. Runs on the
     * viewer's own entity thread (where touching the live inventory is legal) and only for a menu whose spec sets the
     * flag; an ordinary menu never reaches here and never touches the player inventory.
     */
    private void paintBottom(MenuHolder holder, MenuSpec spec, MenuContext ctx, Player live) {
        holder.setBottomSnapshot(live.getInventory().getStorageContents());
        renderer.populateBottom(live.getInventory(), spec, ctx, holder::recordSlot, holder.resolvedLists());
    }

    /**
     * Close every menu this engine owns, cancelling its refresh task first so no timer survives the disable. The
     * online-roster sweep runs on the global region thread (the only thread on which the roster is coherent on
     * Folia), matching the scoreboard/tablist tear-down pattern. The click listener is uninstalled by the bootstrap
     * wiring, not here, so a closed window cannot be re-clicked.
     */
    public void shutdown() {
        scheduler.global(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // The same null guard the quit path takes. A disable sweeps every online player, including one
                // whose view is being torn down as the server stops, and a throw here would leave every window
                // after it in the roster open with its refresh still running.
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof MenuHolder holder) {
                    holder.cancelRefresh();
                    player.closeInventory();
                }
            }
        });
    }
}
