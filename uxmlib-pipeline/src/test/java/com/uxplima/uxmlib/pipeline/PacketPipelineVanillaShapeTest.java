package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The handler has to stand where a packet is a packet, in both directions, on a connection laid out the way the
 * server lays one out: {@code splitter, decoder, prepender, compress, encoder, unbundler, packet_handler}.
 *
 * <p>A packet the server sends is written at the tail and runs towards the head, so it is still a packet until
 * {@code encoder} turns it into bytes. A packet the client sends runs from the head and is a packet once
 * {@code decoder} has read it. Only the place right after {@code encoder} sees a packet both ways. A handler
 * anchored after {@code decoder} was handed bytes on the way out, on a live server, and never saw a sent packet.
 */
class PacketPipelineVanillaShapeTest {

    private final UUID owner = UUID.randomUUID();
    private final List<Object> sent = new ArrayList<>();
    private final List<Object> received = new ArrayList<>();

    @Test
    void aPacketTheServerSendsReachesTheListenerAsAPacket() {
        EmbeddedChannel channel = injected();

        channel.writeOutbound("set_slot");

        assertThat(sent).containsExactly("set_slot");
    }

    @Test
    void aPacketTheClientSendsReachesTheListenerAsAPacket() {
        EmbeddedChannel channel = injected();

        channel.writeInbound(Unpooled.copiedBuffer("use_item", StandardCharsets.UTF_8));

        assertThat(received).containsExactly("use_item");
    }

    @Test
    void aRewriteIsWhatTheClientIsSent() {
        EmbeddedChannel channel = injected();
        registry.register(new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                return PacketAction.PASS;
            }

            @Override
            public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
                return PacketVerdict.rewrite("drawn_" + packet);
            }
        });

        channel.writeOutbound("set_slot");

        ByteBuf wire = channel.readOutbound();
        assertThat(wire.toString(StandardCharsets.UTF_8)).isEqualTo("drawn_set_slot");
    }

    private final PacketListenerRegistry registry = new PacketListenerRegistry();

    private EmbeddedChannel injected() {
        EmbeddedChannel channel = vanillaShaped();
        registry.register(new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                sent.add(packet);
                return PacketAction.PASS;
            }

            @Override
            public PacketAction onReceive(@Nullable UUID player, Object packet) {
                received.add(packet);
                return PacketAction.PASS;
            }
        });
        PacketPipeline pipeline = new PacketPipeline(new ChannelResolver(), registry, "uxmlib_packet", fault -> {});
        assertThat(pipeline.inject(channel, owner)).isTrue();
        return channel;
    }

    /** The server's handler names in the server's order, each doing what the server's does to a message. */
    private static EmbeddedChannel vanillaShaped() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("splitter", new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast("decoder", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ByteBuf in = (ByteBuf) msg;
                String packet = in.toString(StandardCharsets.UTF_8);
                in.release();
                ctx.fireChannelRead(packet);
            }
        });
        channel.pipeline().addLast("prepender", new ChannelOutboundHandlerAdapter());
        channel.pipeline().addLast("compress", new ChannelOutboundHandlerAdapter());
        channel.pipeline().addLast("encoder", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, Unpooled.copiedBuffer((String) msg, StandardCharsets.UTF_8), promise);
            }
        });
        channel.pipeline().addLast("unbundler", new ChannelOutboundHandlerAdapter());
        channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
        return channel;
    }
}
