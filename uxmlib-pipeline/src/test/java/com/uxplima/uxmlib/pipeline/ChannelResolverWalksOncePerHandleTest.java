package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

/**
 * The walk runs once for a player's handle. On a live server one walk took five to ten milliseconds of the
 * main thread, and every packet send asked again, so a sidebar redrawn each second and a few holograms were
 * enough to stall the server. The channel a handle led to is kept; a handle whose channel has closed answers
 * nothing at once, because a closed connection is never reopened and walking again would only search
 * everything behind the player for a channel that is gone.
 */
class ChannelResolverWalksOncePerHandleTest {

    private final ChannelResolver resolver = new ChannelResolver();

    private static EmbeddedChannel withAnchor() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(PacketPipeline.DEFAULT_ANCHOR, new ChannelInboundHandlerAdapter());
        return channel;
    }

    @Test
    void aSecondLookupAnswersWithoutWalkingAgain() {
        EmbeddedChannel channel = withAnchor();
        AtomicReference<Channel> held = new AtomicReference<>(channel);
        Handle handle = new Handle(held);

        assertThat(resolver.resolveHandle(handle)).containsSame(channel);
        held.set(null); // a second walk would now find nothing

        assertThat(resolver.resolveHandle(handle)).containsSame(channel);
    }

    @Test
    void aClosedChannelAnswersNothingAndIsNotSearchedForAgain() {
        EmbeddedChannel first = withAnchor();
        AtomicReference<Channel> held = new AtomicReference<>(first);
        Handle handle = new Handle(held);
        resolver.resolveHandle(handle);

        first.close().syncUninterruptibly();
        held.set(withAnchor()); // a second walk would find this one

        assertThat(resolver.resolveHandle(handle)).isEmpty();
    }

    @Test
    void anotherHandleIsWalkedForItsOwnChannel() {
        EmbeddedChannel one = withAnchor();
        EmbeddedChannel two = withAnchor();

        assertThat(resolver.resolveHandle(new Handle(new AtomicReference<>(one))))
                .containsSame(one);
        assertThat(resolver.resolveHandle(new Handle(new AtomicReference<>(two))))
                .containsSame(two);
    }

    /** Stands in for a server player: the channel sits behind one reference the test can change. */
    @SuppressWarnings("UnusedVariable") // read reflectively by the walk
    private static final class Handle {
        private final AtomicReference<Channel> connection;

        Handle(AtomicReference<Channel> connection) {
            this.connection = connection;
        }
    }
}
