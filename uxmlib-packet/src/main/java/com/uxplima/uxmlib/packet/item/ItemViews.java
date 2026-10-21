package com.uxplima.uxmlib.packet.item;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.packet.item.internal.NmsItemPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketPipeline;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.LanguageWatch;
import org.jspecify.annotations.Nullable;

/**
 * Installs an {@link ItemView} on a server: the interceptor goes into every player's connection, the view
 * sits behind it, and the items that leave for a client go through it on the way out.
 *
 * <p>This is the whole plumbing a consumer would otherwise write again: a channel resolver, a listener
 * registry, the inject on join, the eject on quit, the players already online when the plugin enables, and
 * the delayed reorder pass that puts our handler back after the vanilla encoder when another plugin splices
 * ahead of it. {@code uxmlib-pipeline} deliberately leaves that choreography to its caller; this is the
 * caller for the item case.
 *
 * <p>The handler is named after the plugin, so two plugins that each install a view get one handler each and
 * neither can eject the other's.
 */
public final class ItemViews implements Listener {

    /** The prefix every handler this installs is named with, followed by the plugin's own name. */
    private static final String HANDLER_PREFIX = "uxmlib-item-view-";

    /**
     * How long after a join the handler's position is checked again. Long enough that the plugins which
     * splice their own handlers in on join have finished doing it, short enough that no player plays through
     * it.
     */
    private static final Duration REORDER_DELAY = Duration.ofSeconds(2);

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final PacketPipeline pipeline;
    private final PacketListenerRegistry registry = new PacketListenerRegistry();
    private final ItemViewListener listener;

    /** Each viewer's language as last read on their own thread, for a view drawing on an I/O thread. */
    private final Map<UUID, String> languages = new ConcurrentHashMap<>();

    private @Nullable Function<Player, String> languageOf;
    private Duration languageEvery = Duration.ofSeconds(1);
    private @Nullable TaskHandle languageWatch;

    /**
     * Build the installer against the real server packets. Call {@link #install()} to switch it on.
     *
     * @param plugin the plugin the handler is named after and whose events carry the join and the quit
     * @param scheduler where the delayed reorder pass runs
     * @param view what every viewer is shown in place of the item the server holds
     */
    public ItemViews(Plugin plugin, Scheduler scheduler, ItemView view) {
        this(plugin, scheduler, new NmsItemPackets(), view);
    }

    /**
     * The same, against a supplied {@link ItemPackets}. This is the seam a test drives: the packet layer is
     * the only part that names the server internals, so a fake in its place makes the whole choreography
     * testable without a live server.
     */
    public ItemViews(Plugin plugin, Scheduler scheduler, ItemPackets packets, ItemView view) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(view, "view");
        this.listener = new ItemViewListener(packets, new NamespacedKey(plugin, "shown-from"), view);
        this.registry.register(listener);
        this.pipeline = new PacketPipeline(
                new ChannelResolver(),
                registry,
                handlerName(plugin),
                fault -> plugin.getSLF4JLogger().warn("An item view threw while drawing an item for a client.", fault));
    }

    private static String handlerName(Plugin plugin) {
        return HANDLER_PREFIX + plugin.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Follow each viewer's language, for a view that draws its words in it. Call before {@link #install()}.
     *
     * <p>A view runs on an I/O thread and may not ask the server anything, so the language is read here on the
     * viewer's own thread, on join and every {@code every}, and {@link #languageOf} hands the view that answer.
     * When it changes, the viewer's inventory is sent again: nothing on the server changed, so without the resend
     * the client would keep the old words until the slot happened to change.
     *
     * @param languageOf the language a player reads, asked on their own thread; {@code null} is no answer yet
     * @param every how often each online player is asked
     * @return this, to chain into {@link #install()}
     */
    public ItemViews followLanguage(Function<Player, String> languageOf, Duration every) {
        this.languageOf = Objects.requireNonNull(languageOf, "languageOf");
        this.languageEvery = Objects.requireNonNull(every, "every");
        return this;
    }

    /** {@code viewer}'s language as last read, safe on any thread; empty without {@link #followLanguage}. */
    public Optional<String> languageOf(UUID viewer) {
        return Optional.ofNullable(languages.get(viewer));
    }

    /**
     * Switch the view on: listen for joins and quits, and inject into everyone already online, which is what
     * makes the view work when a plugin is enabled while the server is running.
     */
    public void install() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            pipeline.inject(online);
            readLanguage(online);
        }
        Function<Player, String> language = languageOf;
        if (language != null) {
            languageWatch = LanguageWatch.follow(
                    scheduler, language, languageEvery, plugin.getServer()::getOnlinePlayers, this::languageChanged);
        }
    }

    /** Switch it off again: stop listening, and take the handler back out of every open connection. */
    public void uninstall() {
        HandlerList.unregisterAll(this);
        TaskHandle watch = languageWatch;
        if (watch != null) {
            watch.cancel();
            languageWatch = null;
        }
        languages.clear();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            pipeline.eject(online);
        }
    }

    /** Read {@code player}'s language on their own thread into the snapshot a view reads. */
    private void readLanguage(Player player) {
        Function<Player, String> language = languageOf;
        if (language != null) {
            scheduler.entity(player, () -> remember(player, language.apply(player)));
        }
    }

    /** Called on the player's own thread once per change: draw their items again in the new words. */
    private void languageChanged(Player player) {
        Function<Player, String> language = Objects.requireNonNull(languageOf, "languageOf");
        remember(player, language.apply(player));
        player.updateInventory();
    }

    private void remember(Player player, @Nullable String language) {
        if (language != null) {
            languages.put(player.getUniqueId(), language);
        }
    }

    /** Whether {@code player}'s connection currently carries the handler. */
    public boolean isInjected(Player player) {
        Objects.requireNonNull(player, "player");
        return pipeline.isInjected(player);
    }

    /** The pipeline handler name, which is what a server owner sees when they dump a connection. */
    public String handlerName() {
        return pipeline.handlerName();
    }

    /**
     * Inject on join at MONITOR, so every plugin that mutates the pipeline on the same event has already run
     * and our handler lands on top of what they built rather than under it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void injectOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        pipeline.inject(player);
        readLanguage(player);
        scheduler.entityLater(player, REORDER_DELAY, () -> pipeline.reorder(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void ejectOnQuit(PlayerQuitEvent event) {
        pipeline.eject(event.getPlayer());
        languages.remove(event.getPlayer().getUniqueId());
        listener.forget(event.getPlayer().getUniqueId());
    }
}
