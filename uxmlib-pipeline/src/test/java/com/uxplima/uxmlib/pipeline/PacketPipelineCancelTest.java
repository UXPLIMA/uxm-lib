package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

/**
 * A packet a listener cancels is never sent, and its write still completes: whoever sent it and waits on the write
 * (a plugin that sends a follow-up packet once the first is out) carries on as if it went out.
 */
class PacketPipelineCancelTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final List<Object> written = new ArrayList<>();

    private final PacketListenerRegistry listeners = new PacketListenerRegistry();

    private final PacketPipeline pipeline =
            new PacketPipeline(new ChannelResolver(), listeners, "uxmlib-test", fault -> {});

    private EmbeddedChannel channel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(PacketPipeline.DEFAULT_ANCHOR, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                written.add(msg);
                promise.setSuccess();
            }
        });
        listeners.register((player, packet) -> "drop".equals(packet) ? PacketVerdict.cancel() : PacketVerdict.pass());
        assertThat(pipeline.inject(channel, ALEX)).isTrue();
        return channel;
    }

    @Test
    void aCancelledPacketIsNotSentButItsWriteCompletes() {
        EmbeddedChannel channel = channel();

        ChannelFuture write = channel.writeAndFlush("drop");
        channel.runPendingTasks();

        assertThat(written).isEmpty();
        assertThat(write.isDone()).isTrue();
        assertThat(write.isSuccess()).isTrue();
    }

    @Test
    void aPassedPacketIsSentAndItsWriteCompletes() {
        EmbeddedChannel channel = channel();

        ChannelFuture write = channel.writeAndFlush("keep");
        channel.runPendingTasks();

        assertThat(written).containsExactly("keep");
        assertThat(write.isSuccess()).isTrue();
    }
}
