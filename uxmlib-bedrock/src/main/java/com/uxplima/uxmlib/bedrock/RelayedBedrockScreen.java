package com.uxplima.uxmlib.bedrock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A {@link BedrockScreen} whose forms a plugin carries somewhere else to be sent, such as to a proxy that runs Geyser
 * for a backend with neither Floodgate nor Geyser of its own. Each form goes to the plugin's {@link Transport} as a
 * {@link BedrockFormRequest} with a request id; the plugin hands the answer back through {@link #receive}, and the
 * caller's callbacks run as they would for a form Floodgate sent.
 *
 * <p>An answer is taken only for the player its request was sent to, and only once. One that matches no request is
 * ignored, so a stale, repeated or misrouted answer runs nothing. A player keeps at most {@value #MAX_PENDING}
 * unanswered forms; past that the oldest is dropped. When the player leaves this server the plugin calls
 * {@link #forget}, which drops theirs without running any callback, as leaving does for a form sent here.
 *
 * <p>An answer may carry a secret, such as a PIN typed into an input. This class never logs one, and the plugin's
 * relay must send it only from the server that showed the form to the server that asked: never to the client, never
 * to another server.
 */
public final class RelayedBedrockScreen extends FormScreen {

    /** The most unanswered forms one player keeps. */
    public static final int MAX_PENDING = 8;

    private static final Logger LOG = Logger.getLogger(RelayedBedrockScreen.class.getName());

    /** Carries a form from this screen to whatever sends it to the player. */
    @FunctionalInterface
    public interface Transport {

        /**
         * Sends {@code request} for {@code player}, to be answered through {@link RelayedBedrockScreen#receive} under
         * the same {@code requestId}. {@link BedrockFormCodec} turns the request into bytes.
         *
         * @param player the Bedrock player the form is for
         * @param requestId the id the answer comes back under
         * @param request the form
         * @return {@code false} when the form cannot be carried, such as when no relay is connected
         */
        boolean send(UUID player, long requestId, BedrockFormRequest request);
    }

    private final Transport transport;
    private final AtomicLong ids = new AtomicLong(ThreadLocalRandom.current().nextLong());

    /** Unanswered forms per player, oldest first. Guarded by {@code this}. */
    private final Map<UUID, LinkedHashMap<Long, Consumer<BedrockFormResponse>>> pending = new HashMap<>();

    /**
     * A screen that sends through {@code transport}.
     *
     * @param transport the plugin's carrier for each form; never {@code null}
     */
    public RelayedBedrockScreen(Transport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /**
     * Hands the answer to request {@code requestId} to the callbacks of the form it answers. Callbacks run on the
     * calling thread; a caller's callback hops to the player's own thread before it touches the world.
     *
     * @param player the player who answered
     * @param requestId the id the request was sent under
     * @param response the answer
     * @return {@code true} when it answered a form sent to {@code player} that was still waiting
     */
    public boolean receive(UUID player, long requestId, BedrockFormResponse response) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(response, "response");
        Consumer<BedrockFormResponse> waiting = take(player, requestId);
        if (waiting == null) {
            return false;
        }
        waiting.accept(response);
        return true;
    }

    /**
     * Drops every form still waiting for {@code player}, running none of their callbacks. A plugin calls it when the
     * player leaves this server, by quitting or by switching to another.
     *
     * @param player the player who left
     */
    public synchronized void forget(UUID player) {
        pending.remove(Objects.requireNonNull(player, "player"));
    }

    @Override
    void send(Player player, BedrockFormRequest request, Consumer<BedrockFormResponse> onResponse) {
        UUID id = player.getUniqueId();
        long requestId = ids.incrementAndGet();
        hold(id, requestId, onResponse);
        boolean sent;
        try {
            sent = transport.send(id, requestId, request);
        } catch (RuntimeException failed) {
            take(id, requestId);
            LOG.warning("event=bedrock_relay_failed player=" + player.getName() + " reason=" + failed.getMessage());
            return;
        }
        if (!sent) {
            take(id, requestId);
            LOG.warning("event=bedrock_relay_undelivered player=" + player.getName());
        }
    }

    private synchronized void hold(UUID player, long requestId, Consumer<BedrockFormResponse> onResponse) {
        LinkedHashMap<Long, Consumer<BedrockFormResponse>> theirs =
                pending.computeIfAbsent(player, unused -> new LinkedHashMap<>());
        theirs.put(requestId, onResponse);
        if (theirs.size() > MAX_PENDING) {
            Iterator<Long> oldest = theirs.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private synchronized @Nullable Consumer<BedrockFormResponse> take(UUID player, long requestId) {
        LinkedHashMap<Long, Consumer<BedrockFormResponse>> theirs = pending.get(player);
        if (theirs == null) {
            return null;
        }
        Consumer<BedrockFormResponse> waiting = theirs.remove(requestId);
        if (theirs.isEmpty()) {
            pending.remove(player);
        }
        return waiting;
    }
}
