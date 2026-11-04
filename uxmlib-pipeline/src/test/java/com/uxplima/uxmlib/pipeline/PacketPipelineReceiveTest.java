package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

/** A packet handed in as if the client sent it reaches the server's handler, and only when one is there to read it. */
class PacketPipelineReceiveTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final List<Object> read = new ArrayList<>();

    private final PacketPipeline pipeline =
            new PacketPipeline(new ChannelResolver(), new PacketListenerRegistry(), "uxmlib-test", fault -> {});

    private EmbeddedChannel channel(boolean withServerHandler) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(PacketPipeline.DEFAULT_ANCHOR, new ChannelOutboundHandlerAdapter());
        if (withServerHandler) {
            channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    read.add(msg);
                }
            });
        }
        return channel;
    }

    @Test
    void aPacketReachesTheHandlerAfterOurs() {
        EmbeddedChannel channel = channel(true);
        assertThat(pipeline.inject(channel, ALEX)).isTrue();

        boolean sent = pipeline.receive(channel, "click");
        channel.runPendingTasks();

        assertThat(sent).isTrue();
        assertThat(read).containsExactly("click");
    }

    @Test
    void withNothingAfterOurHandlerNothingIsSent() {
        EmbeddedChannel channel = channel(false);
        pipeline.inject(channel, ALEX);

        assertThat(pipeline.receive(channel, "click")).isFalse();
    }

    @Test
    void aChannelWithoutOurHandlerSendsNothing() {
        assertThat(pipeline.receive(channel(true), "click")).isFalse();
    }
}
