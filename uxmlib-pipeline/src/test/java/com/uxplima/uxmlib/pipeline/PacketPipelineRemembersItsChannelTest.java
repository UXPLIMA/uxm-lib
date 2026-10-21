package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A player's handler is taken out of the channel it was put into. By the time a player quits, the connection
 * is usually closed, and a closed channel is one the resolver's walk never stops at: it searches the whole
 * object graph behind the player, which on a live server held the main thread past the watchdog. So eject
 * does not resolve the player again. MockBukkit has no channel to resolve, which makes the difference
 * visible: only a pipeline that remembers the channel can take its handler out.
 */
class PacketPipelineRemembersItsChannelTest {

    private static final String HANDLER = "uxmlib_packet";

    private ServerMock server;
    private PacketPipeline pipeline;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        pipeline = new PacketPipeline(new ChannelResolver(), new PacketListenerRegistry(), HANDLER, fault -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static EmbeddedChannel channelWithAnchor() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(PacketPipeline.DEFAULT_ANCHOR, new ChannelInboundHandlerAdapter());
        return channel;
    }

    @Test
    void ejectTakesTheHandlerOutOfTheChannelItWasInjectedInto() {
        PlayerMock player = server.addPlayer();
        EmbeddedChannel channel = channelWithAnchor();
        pipeline.inject(channel, player.getUniqueId());

        assertThat(pipeline.eject(player)).isTrue();
        assertThat(channel.pipeline().get(HANDLER)).isNull();
    }

    @Test
    void isInjectedAnswersFromTheRememberedChannel() {
        PlayerMock player = server.addPlayer();
        EmbeddedChannel channel = channelWithAnchor();
        pipeline.inject(channel, player.getUniqueId());

        assertThat(pipeline.isInjected(player)).isTrue();
        pipeline.eject(player);
        assertThat(pipeline.isInjected(player)).isFalse();
    }

    @Test
    void aClosedChannelIsForgottenAndEjectHasNothingToDo() {
        PlayerMock player = server.addPlayer();
        EmbeddedChannel channel = channelWithAnchor();
        pipeline.inject(channel, player.getUniqueId());

        channel.close().syncUninterruptibly();

        assertThat(pipeline.isInjected(player)).isFalse();
        assertThat(pipeline.eject(player)).isFalse();
    }
}
