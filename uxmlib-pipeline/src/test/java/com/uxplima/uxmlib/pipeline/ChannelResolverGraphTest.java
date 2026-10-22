package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

/**
 * Drives the reflective object-graph walk directly with synthetic handle objects (MockBukkit has no
 * {@code getHandle()} chain to walk). Real {@link EmbeddedChannel}s stand in for the connection channel so the
 * anchor-preference and container-unwrapping behaviour can be asserted without a live server.
 */
class ChannelResolverGraphTest {

    private final ChannelResolver resolver = new ChannelResolver();

    private static EmbeddedChannel withAnchor() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(PacketPipeline.DEFAULT_ANCHOR, new ChannelInboundHandlerAdapter());
        return channel;
    }

    @Test
    void prefersTheChannelWhosePipelineCarriesTheAnchorOverAnyOtherChannel() {
        EmbeddedChannel decoy = new EmbeddedChannel(); // open, but no "decoder": must not be chosen
        EmbeddedChannel gameplay = withAnchor();
        // Field order is deliberate: the decoy is declared first, so a blind first-Channel walk would pick it.
        Object handle = new TwoChannelHandle(decoy, gameplay);

        assertThat(resolver.findChannel(handle)).isSameAs(gameplay);
    }

    @Test
    void fallsBackToTheFirstOpenChannelWhenNoneCarriesTheAnchor() {
        EmbeddedChannel only = new EmbeddedChannel(); // open but anchorless (e.g. early handshake)
        Object handle = new CollectionHandle(List.of(only));

        assertThat(resolver.findChannel(handle)).isSameAs(only);
    }

    @Test
    void reachesAChannelHeldBehindAnAtomicReference() {
        EmbeddedChannel channel = withAnchor();
        Object handle = new AtomicHandle(new AtomicReference<>(channel));

        assertThat(resolver.findChannel(handle)).isSameAs(channel);
    }

    @Test
    void reachesAChannelHeldInsideACollection() {
        EmbeddedChannel channel = withAnchor();
        Object handle = new CollectionHandle(List.of(channel));

        assertThat(resolver.findChannel(handle)).isSameAs(channel);
    }

    @Test
    void reachesAChannelHeldInAnInheritedSuperclassField() {
        // Since 1.20.2 the channel-bearing connection field sits on a superclass of the game packet listener,
        // so the walk must read inherited fields, not just the leaf class's own.
        EmbeddedChannel channel = withAnchor();
        Object handle = new SubListener(channel);

        assertThat(resolver.findChannel(handle)).isSameAs(channel);
    }

    @Test
    void skipsACollectionWhoseIteratorThrowsAndStillFindsTheChannel() {
        // A 26.1.2 ServerPlayer graph reaches a specialised redstone queue whose iterator() throws
        // UnsupportedOperationException; the walk must fail closed on that field and keep going, not abort.
        EmbeddedChannel channel = withAnchor();
        Object handle = new UniterableHandle(new ThrowingCollection(), channel);

        assertThat(resolver.findChannel(handle)).isSameAs(channel);
    }

    @Test
    void aPlayerWithAFakeConnectionNeverResolvesToAnotherPlayersChannelOnTheServersRoster() {
        // A bot or an NPC holds a channel of its own that carries no encoder, and through its server it reaches
        // the set of every client's channel. The walk took the first encoder-bearing channel in that set, so a
        // sidebar drawn for a bot reached a real player, who then saw the same objective created twice and was
        // disconnected.
        EmbeddedChannel own = new EmbeddedChannel();
        EmbeddedChannel someoneElse = withAnchor();
        Object handle = new FakePlayerHandle(own, new ServerHandle(new java.util.HashSet<>(List.of(someoneElse))));

        assertThat(resolver.findChannel(handle)).isSameAs(own);
    }

    /** Shaped as a player whose connection is fake: its own channel first, then the server it can reach. */
    private static final class FakePlayerHandle {
        @SuppressWarnings("unused")
        private final Connection connection;

        @SuppressWarnings("unused")
        private final ServerHandle server;

        FakePlayerHandle(Channel own, ServerHandle server) {
            this.connection = new Connection(own);
            this.server = server;
        }
    }

    /** The server holds every client's channel in one set, as the connection listener does. */
    private static final class ServerHandle {
        @SuppressWarnings("unused")
        private final java.util.Set<Channel> injectedClients;

        ServerHandle(java.util.Set<Channel> injectedClients) {
            this.injectedClients = injectedClients;
        }
    }

    private static final class TwoChannelHandle {
        @SuppressWarnings("unused")
        private final Channel first;

        @SuppressWarnings("unused")
        private final Connection connection;

        TwoChannelHandle(Channel first, Channel second) {
            this.first = first;
            this.connection = new Connection(second);
        }
    }

    private static final class Connection {
        @SuppressWarnings("unused")
        private final Channel channel;

        Connection(Channel channel) {
            this.channel = channel;
        }
    }

    private static final class AtomicHandle {
        @SuppressWarnings("unused")
        private final AtomicReference<Channel> ref;

        AtomicHandle(AtomicReference<Channel> ref) {
            this.ref = ref;
        }
    }

    private static final class CollectionHandle {
        @SuppressWarnings("unused")
        private final List<Channel> channels;

        CollectionHandle(List<Channel> channels) {
            this.channels = channels;
        }
    }

    private static class BaseListener {
        @SuppressWarnings("unused")
        private final Connection connection;

        BaseListener(Channel channel) {
            this.connection = new Connection(channel);
        }
    }

    /** The connection field lives on {@link BaseListener}, so only an inherited-field walk reaches the channel. */
    private static final class SubListener extends BaseListener {
        SubListener(Channel channel) {
            super(channel);
        }
    }

    /** Declares the throwing collection first so the walk must survive it before reaching the connection. */
    private static final class UniterableHandle {
        @SuppressWarnings("unused")
        private final java.util.Collection<Object> queue;

        @SuppressWarnings("unused")
        private final Connection connection;

        UniterableHandle(java.util.Collection<Object> queue, Channel channel) {
            this.queue = queue;
            this.connection = new Connection(channel);
        }
    }

    /** A collection that refuses iteration, mirroring the redstone queue that surfaced the resolver bug. */
    private static final class ThrowingCollection extends java.util.AbstractCollection<Object> {
        @Override
        public java.util.Iterator<Object> iterator() {
            throw new UnsupportedOperationException("not iterable");
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
