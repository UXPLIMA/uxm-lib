package com.uxplima.uxmlib.pipeline;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;

/**
 * The one class that holds the unavoidable NMS reflection. It reaches a player's raw Netty
 * {@link Channel} via {@code CraftPlayer.getHandle() -> connection -> channel}, walking by field
 * <em>type</em> rather than obfuscated field <em>name</em> so it survives Mojang's remapping across 1.21.x.
 * Every step is guarded; the whole thing returns an {@link Optional} and <b>never throws</b>: on any mock
 * player, security manager, or layout change it simply returns {@link Optional#empty()}.
 *
 * <p>A {@code ServerPlayer} object graph can expose more than one {@link Channel}-typed reference (an
 * embedded/local channel for integrated-server play, a wrapped child channel), so the walk does not blindly
 * take the first Channel it sees: it prefers a channel whose pipeline actually carries the gameplay
 * {@code anchor} handler (the vanilla {@code "encoder"}), and only falls back to the first open channel when
 * no anchor-bearing one is found. That keeps the interceptor from anchoring onto a channel that never sees
 * gameplay packets.
 *
 * <p>Why so much reflection: Paper exposes no public handle to the connection's Netty channel, and a
 * from-scratch (MIT) packet layer cannot use PacketEvents (GPL) to get it. Isolating it in this one class
 * keeps the rest of the module free of NMS. Every {@code setAccessible}/read is guarded and fails closed.
 *
 * <p>The walk runs once per player handle. It reads thousands of fields and collection elements, several
 * milliseconds of the calling thread, and a packet sender asks for the channel on every send. The answer a
 * handle led to is kept, held weakly on both sides, and a handle whose channel has closed answers empty at once:
 * a connection is never reopened. A walk that found nothing is remembered too, but only for {@link #MISS_RETRY}:
 * a fake connection never gains a channel, so asking on every send would walk on every send, while a real player
 * whose walk once came back empty must not stay without packets for the rest of their session.
 *
 * <p>The walk is bounded three ways, because it runs on the thread that lets a player in and every plugin
 * that carries this library walks for itself. A player whose connection is fake, a bot or an NPC, holds a
 * channel with no anchor, and an unbounded walk then searched everything its server could reach for one that
 * had, long enough for the watchdog to stop the server. So the first channel found ends the search one level
 * past it, the objects a walk may read are capped, and the fields of a class are read once and kept.
 */
public final class ChannelResolver {

    /** How deep into nested connection objects we search for a Channel field before giving up. */
    private static final int MAX_DEPTH = 4;

    /**
     * How many objects one walk may read before it gives up. A real player's channel is two references from the
     * player and is met within the first few thousand; the cap is there for a graph nobody foresaw, so that the
     * worst a walk can cost is bounded rather than whatever the server happens to hold.
     */
    private static final int MAX_OBJECTS = 20_000;

    /** How long a handle whose walk found no channel answers empty before it is walked again. */
    static final java.time.Duration MISS_RETRY = java.time.Duration.ofSeconds(30);

    /**
     * The readable fields of each class, its superclasses' included, worked out once. Asking the class again for
     * every object it walked copied the arrays each time, and that copy was most of what a long walk cost.
     */
    private static final ClassValue<Field[]> FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            return readableFields(type);
        }
    };

    private final String anchorName;
    private final int maxObjects;

    /** What each handle's walk answered. Weak keys let a handle go when its player does; a found channel is held
     * weakly, because it leads back to the handle and would otherwise hold its own key. */
    private final Map<Object, Answer> found = Collections.synchronizedMap(new WeakHashMap<>());

    private final java.util.function.LongSupplier nanoClock;

    /** Resolve preferring a channel whose pipeline carries the default {@code "encoder"} anchor. */
    public ChannelResolver() {
        this(PacketPipeline.DEFAULT_ANCHOR);
    }

    /**
     * @param anchorName the pipeline handler a gameplay channel must contain to be preferred over any other
     *     open channel reachable in the object graph (e.g. {@code "encoder"})
     */
    public ChannelResolver(String anchorName) {
        this(anchorName, MAX_OBJECTS);
    }

    /** Package-private so a test can show the cap ending a walk without building a graph of twenty thousand. */
    ChannelResolver(String anchorName, int maxObjects) {
        this(anchorName, maxObjects, System::nanoTime);
    }

    /** Package-private so a test can move time past a remembered miss. */
    ChannelResolver(String anchorName, int maxObjects, java.util.function.LongSupplier nanoClock) {
        this.anchorName = Objects.requireNonNull(anchorName, "anchorName");
        if (maxObjects < 1) {
            throw new IllegalArgumentException("maxObjects must be at least 1, got: " + maxObjects);
        }
        this.maxObjects = maxObjects;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /**
     * Resolve the Netty channel backing {@code player}'s connection.
     *
     * @return the channel, or empty if the player is not a CraftPlayer / the layout could not be walked / the
     *     channel is not open
     */
    public Optional<Channel> resolve(Player player) {
        Objects.requireNonNull(player, "player");
        @Nullable Object handle = invokeNoArg(player, "getHandle");
        if (handle == null) {
            return Optional.empty();
        }
        return resolveHandle(handle);
    }

    /** The channel behind a player's server handle. Package-private so a test can hand it a synthetic handle. */
    Optional<Channel> resolveHandle(Object handle) {
        @Nullable Answer kept = found.get(handle);
        if (kept instanceof Found(WeakReference<Channel> reference)) {
            @Nullable Channel channel = reference.get();
            return channel != null && channel.isOpen() ? Optional.of(channel) : Optional.empty();
        }
        long now = nanoClock.getAsLong();
        if (kept instanceof Missed(long at) && now - at < MISS_RETRY.toNanos()) {
            return Optional.empty();
        }
        @Nullable Channel channel = findChannel(handle);
        found.put(handle, channel == null ? new Missed(now) : new Found(new WeakReference<>(channel)));
        return Optional.ofNullable(channel);
    }

    /**
     * Breadth-first search for an open {@link Channel} reachable from {@code root}. A channel held in a field and
     * whose pipeline holds the {@code anchor} handler is returned the moment it is found; any other open channel
     * is kept only as a fallback and returned if the walk finds no anchor-bearing one.
     *
     * <p>A channel picked out of a collection is a roster entry rather than the player's own: a server keeps every
     * client's channel in one set, and a player whose connection is fake (a bot, an NPC) reaches that set through
     * its server. Such a channel is taken only when the walk finds no channel held in a field at all, or the
     * player's packets would reach somebody else.
     *
     * <p>A channel held in a field ends the search one level past the object that held it: the objects at that
     * level are still read, because the player's own connection may sit beside the first channel met, but nothing
     * deeper is opened. A fake player's own channel is met two references in, and another client's connection
     * only ever lies further out. A walk that reaches its cap of objects stops where it is and answers what a
     * field held so far, never a roster entry, because a roster entry is somebody else's.
     *
     * <p>Package-private so the object-graph walk can be exercised directly with a synthetic handle, since
     * MockBukkit cannot give us a {@code CraftPlayer.getHandle()} chain to walk.
     */
    @Nullable Channel findChannel(Object root) {
        Deque<Node> queue = new ArrayDeque<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(new Node(root, MAX_DEPTH));
        seen.add(root);
        Candidates held = new Candidates();
        Candidates listed = new Candidates();
        int heldAt = -1;
        int walked = 0;
        while (!queue.isEmpty()) {
            if (++walked > maxObjects) {
                return held.best();
            }
            Node node = queue.removeFirst();
            boolean descend = node.depth > 0 && node.depth >= heldAt;
            for (Field field : FIELDS.get(node.value.getClass())) {
                @Nullable Object raw = read(field, node.value);
                @Nullable Object value = unwrap(raw);
                if (value instanceof Channel channel) {
                    boolean inField = !(raw instanceof Collection<?>);
                    if (inField && channel.isOpen() && hasAnchor(channel)) {
                        return channel;
                    }
                    (inField ? held : listed).offer(channel, hasAnchor(channel));
                    if (inField && heldAt < 0) {
                        heldAt = node.depth;
                    }
                } else if (value != null && descend && shouldDescend(field) && seen.add(value)) {
                    queue.add(new Node(value, node.depth - 1));
                }
            }
        }
        @Nullable Channel own = held.best();
        return own != null ? own : listed.best();
    }

    /** The first open channel a walk met, and the first that carried the anchor. */
    private static final class Candidates {
        private @Nullable Channel anchored;
        private @Nullable Channel any;

        void offer(Channel channel, boolean anchor) {
            if (!channel.isOpen()) {
                return;
            }
            if (anchor && anchored == null) {
                anchored = channel;
            }
            if (any == null) {
                any = channel;
            }
        }

        @Nullable Channel best() {
            return anchored != null ? anchored : any;
        }
    }

    private boolean hasAnchor(Channel channel) {
        try {
            return channel.pipeline().get(anchorName) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Descend into reference fields, but never into primitives or JDK leaf types (String, boxes, time/math). */
    private static boolean shouldDescend(Field field) {
        Class<?> type = field.getType();
        if (type.isPrimitive() || type.isEnum()) {
            return false;
        }
        String name = type.getName();
        return !name.startsWith("java.lang.")
                && !name.startsWith("java.time.")
                && !name.startsWith("java.math.")
                && !name.startsWith("java.io.")
                && !name.startsWith("java.net.")
                && !name.startsWith("java.nio.")
                && !name.startsWith("[");
    }

    /** Peel a single layer of {@link AtomicReference}/{@link Optional}/{@link Collection} that may hold the channel. */
    private static @Nullable Object unwrap(@Nullable Object value) {
        if (value instanceof AtomicReference<?> ref) {
            return ref.get();
        }
        if (value instanceof Optional<?> opt) {
            return opt.orElse(null);
        }
        if (value instanceof Collection<?> collection) {
            try {
                for (Object element : collection) {
                    if (element instanceof Channel) {
                        return element;
                    }
                }
            } catch (RuntimeException uniterable) {
                // Some game-state collections reachable in the object graph (e.g. a specialised redstone queue
                // whose iterator() throws UnsupportedOperationException) cannot be walked. The resolver's
                // contract is to fail closed, so treat such a field as carrying no channel and keep walking the
                // rest of the graph rather than letting the exception escape and abort every packet send.
                return null;
            }
        }
        return value;
    }

    /**
     * Every field declared on {@code type} <em>and its superclasses</em>. Walking the hierarchy is essential:
     * since 1.20.2 the {@code Connection} that holds the channel is reached through the {@code connection} field
     * declared on {@code ServerCommonPacketListenerImpl}, a superclass of the game packet listener, so a
     * declared-fields-only walk stops at the listener and never reaches the channel.
     *
     * <p>Only instance fields the walk can open are kept. A connection is never held in a static field, and a
     * static field leads to state the whole server shares, which is where an unbounded walk spent its time.
     */
    private static Field[] readableFields(Class<?> type) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field field : c.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.trySetAccessible()) {
                        fields.add(field);
                    }
                }
            } catch (SecurityException ignored) {
                // Skip an unreadable level and keep walking up; a missing level just narrows the search.
            }
        }
        return fields.toArray(new Field[0]);
    }

    private static @Nullable Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // RuntimeException covers SecurityException/InaccessibleObjectException; on any of them we fail
            // closed and the channel simply resolves to empty.
            return null;
        }
    }

    private static @Nullable Object invokeNoArg(Object target, String method) {
        @Nullable Method found = findNoArgMethod(target.getClass(), method);
        if (found == null) {
            return null;
        }
        try {
            found.setAccessible(true);
            return found.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            // RuntimeException covers SecurityException/InaccessibleObjectException; fail closed to empty.
            return null;
        }
    }

    /** Find a no-arg method by name, walking the class hierarchy so a non-public {@code getHandle} still resolves. */
    private static @Nullable Method findNoArgMethod(Class<?> type, String method) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(method);
            } catch (NoSuchMethodException | SecurityException ignored) {
                // Try the superclass; a public getHandle() is found on the first hop, a relocated one later.
            }
        }
        return null;
    }

    /** A graph node paired with the remaining descent budget. */
    private record Node(Object value, int depth) {}

    /** What a handle's walk answered: a channel, or nothing as of a moment on the nano clock. */
    private sealed interface Answer permits Found, Missed {}

    private record Found(WeakReference<Channel> channel) implements Answer {}

    private record Missed(long at) implements Answer {}
}
