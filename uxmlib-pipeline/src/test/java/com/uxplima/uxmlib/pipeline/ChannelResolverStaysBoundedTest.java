package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The walk runs on the thread that lets a player in, once for every library copy on the server. A player whose
 * connection is fake, a bot or an NPC, has a channel with no encoder, so the walk used to search everything its
 * server could reach for one that had: a single bot joining held the main thread long enough for the watchdog to
 * stop the server. These pin the three limits that keep a walk short whatever the player is.
 */
class ChannelResolverStaysBoundedTest {

    private static EmbeddedChannel withAnchor() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(PacketPipeline.DEFAULT_ANCHOR, new ChannelInboundHandlerAdapter());
        return channel;
    }

    @Test
    void aChannelFoundEndsTheSearchOneLevelPastIt() {
        // The bot's own channel sits close; an encoder-bearing channel sits two levels further out, the way
        // another client's connection sits somewhere behind the server. The near one is the player's own.
        EmbeddedChannel own = new EmbeddedChannel();
        EmbeddedChannel far = withAnchor();
        Object handle = new Near(new Connection(own), new Box(new Box(new Box(null, new Connection(far)), null), null));

        assertThat(new ChannelResolver().findChannel(handle)).isSameAs(own);
    }

    @Test
    void aWalkStopsAtItsBudgetAndAnswersNothingRatherThanHoldTheThread() {
        EmbeddedChannel channel = withAnchor();
        Object handle = new Late(new Box(new Box(null, null), null), new Connection(channel));

        assertThat(new ChannelResolver(PacketPipeline.DEFAULT_ANCHOR, 2).findChannel(handle))
                .isNull();
        assertThat(new ChannelResolver(PacketPipeline.DEFAULT_ANCHOR, 100).findChannel(handle))
                .isSameAs(channel);
    }

    @Test
    void aHandleWithNoChannelIsNotWalkedAgainUntilItsRetryIsDue() {
        // A fake connection never gains a channel, so asking on every send would walk on every send. A miss is
        // remembered for a while and then tried again, so a real player whose walk once came back empty is not
        // left without packets for the rest of their session.
        AtomicReference<@Nullable Channel> held = new AtomicReference<>();
        Object handle = new Holder(held);
        AtomicLong now = new AtomicLong();
        ChannelResolver resolver = new ChannelResolver(PacketPipeline.DEFAULT_ANCHOR, 100, now::get);

        assertThat(resolver.resolveHandle(handle)).isEmpty();
        EmbeddedChannel channel = withAnchor();
        held.set(channel); // a second walk would find this one

        now.addAndGet(ChannelResolver.MISS_RETRY.toNanos() - 1);
        assertThat(resolver.resolveHandle(handle)).isEmpty();

        now.addAndGet(1);
        assertThat(resolver.resolveHandle(handle)).containsSame(channel);
    }

    @SuppressWarnings("unused") // read reflectively by the walk
    private record Near(Connection connection, Box server) {}

    @SuppressWarnings("unused") // read reflectively by the walk
    private record Late(Box first, Connection connection) {}

    @SuppressWarnings("unused") // read reflectively by the walk
    private record Box(@Nullable Box inner, @Nullable Connection connection) {}

    @SuppressWarnings("unused") // read reflectively by the walk
    private record Connection(Channel channel) {}

    @SuppressWarnings("unused") // read reflectively by the walk
    private record Holder(AtomicReference<@Nullable Channel> connection) {}
}
