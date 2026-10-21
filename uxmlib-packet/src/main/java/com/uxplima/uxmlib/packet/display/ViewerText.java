package com.uxplima.uxmlib.packet.display;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.packet.display.internal.NmsDisplayTextPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.LanguageWatch;
import org.jspecify.annotations.Nullable;

/**
 * A shared {@link TextDisplay} that each viewer reads in their own words: their language, their name, their
 * values. The entity stays one real entity with one broadcast text; each viewer who sees it is sent a
 * {@link DisplayTextPackets#textOverride text override} computed for them.
 *
 * <p>The server starts tracking an entity as it is added to the world, and a copy sent at that moment reaches
 * a client that does not hold the entity yet and is dropped. So a viewer who starts tracking a bound display is
 * sent their text {@link #TRACKING_DELAY} later, on their own thread, once the client has it. A display bound
 * while players already see it is sent to them at once, and {@link #refresh(Player)} redraws everything a viewer
 * sees, for a viewer who changed language or a line that changed.
 *
 * <p>Register it as a listener once: {@code server.getPluginManager().registerEvents(viewerText, plugin)}. Bind a
 * display on its own region thread after spawning it, and unbind it before removing it.
 */
public final class ViewerText implements Listener {

    /** Two ticks: one tick in, a live client still had no entity to apply the text to. */
    static final Duration TRACKING_DELAY = Duration.ofMillis(100);

    private final Scheduler scheduler;
    private final DisplayTextPackets packets;
    private final Map<Integer, Bound> bound = new ConcurrentHashMap<>();

    private record Bound(TextDisplay display, Function<Player, Component> text) {}

    /**
     * A ViewerText that writes real packets to each viewer's connection. The packet classes are reached on the
     * first send, not here, and a server without them (a test server) is sent nothing.
     */
    public static ViewerText live(Scheduler scheduler) {
        return new ViewerText(scheduler, new LivePackets());
    }

    public ViewerText(Scheduler scheduler, DisplayTextPackets packets) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    /**
     * Give each viewer of {@code display} the text {@code text} computes for them, from now on. Anyone already
     * tracking it is sent theirs at once. Call on the display's region thread.
     */
    public void bind(TextDisplay display, Function<Player, Component> text) {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(text, "text");
        Bound binding = new Bound(display, text);
        bound.put(display.getEntityId(), binding);
        for (Player viewer : display.getTrackedBy()) {
            send(viewer, binding);
        }
    }

    /** Stop overriding {@code display}. A send still waiting for it is dropped. */
    public void unbind(TextDisplay display) {
        Objects.requireNonNull(display, "display");
        bound.remove(display.getEntityId());
    }

    /** Send {@code viewer} their text for every bound display they already see. Call on the viewer's thread. */
    public void refresh(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        for (Bound binding : bound.values()) {
            if (binding.display().isValid() && binding.display().getTrackedBy().contains(viewer)) {
                send(viewer, binding);
            }
        }
    }

    /**
     * Watch each online player's language and send them everything they see again when it changes. Nothing tells
     * every plugin on a server that a player chose a language, so the answer is asked of {@code languageOf} every
     * {@code every}, on each player's own thread. The first answer is remembered and sends nothing.
     *
     * @param languageOf the language a player reads, compared with {@code equals}
     * @param online who is on the server, asked on the global thread
     * @return the timer, for the caller to cancel when it shuts down
     */
    public TaskHandle followLanguage(
            Function<Player, ?> languageOf, Duration every, Supplier<? extends Collection<? extends Player>> online) {
        return LanguageWatch.follow(scheduler, languageOf, every, online, this::refresh);
    }

    /** Unbind everything, for a plugin shutting down or reloading. */
    public void clear() {
        bound.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        int entityId = event.getEntity().getEntityId();
        if (!bound.containsKey(entityId)) {
            return;
        }
        Player viewer = event.getPlayer();
        scheduler.entityLater(viewer, TRACKING_DELAY, () -> {
            Bound binding = bound.get(entityId);
            if (binding != null && viewer.isOnline()) {
                send(viewer, binding);
            }
        });
    }

    private void send(Player viewer, Bound binding) {
        Component text = binding.text().apply(viewer);
        packets.send(viewer, packets.textOverride(binding.display().getEntityId(), text));
    }

    /** The real packets, built on first use; nothing at all where the server's own classes are missing. */
    private static final class LivePackets implements DisplayTextPackets {

        private static final Object NOTHING = new Object();

        private volatile @Nullable DisplayTextPackets real;
        private volatile boolean missing;

        private @Nullable DisplayTextPackets real() {
            DisplayTextPackets built = real;
            if (built != null || missing) {
                return built;
            }
            try {
                built = new NmsDisplayTextPackets(new PacketSender(new ChannelResolver()));
                real = built;
                return built;
            } catch (LinkageError absent) {
                missing = true;
                return null;
            }
        }

        @Override
        public Object textOverride(int entityId, Component text) {
            DisplayTextPackets built = real();
            if (built == null) {
                return NOTHING;
            }
            try {
                return built.textOverride(entityId, text);
            } catch (LinkageError absent) {
                missing = true;
                return NOTHING;
            }
        }

        @Override
        public void send(Player viewer, Object packet) {
            DisplayTextPackets built = real;
            if (built != null && packet != NOTHING) {
                built.send(viewer, packet);
            }
        }
    }
}
