package com.uxplima.uxmlib.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/**
 * A server's connection to the {@link DirectBroker} its proxy runs.
 *
 * <p>The connection is opened by the server and kept open: a server with no player online still sends
 * and receives, which plugin messaging through a player cannot do. When the link drops, frames wait in an
 * {@link Outbox} and the connection is tried again with a growing pause, up to half a minute.
 */
public final class DirectBus implements Bus {

    private static final int OUTBOX = 4096;
    private static final Duration FIRST_PAUSE = Duration.ofSeconds(1);
    private static final Duration LONGEST_PAUSE = Duration.ofSeconds(30);

    private final String serverId;
    private final InetSocketAddress broker;
    private final Signer signer;
    private final System.Logger log;
    private final Handlers handlers;
    private final Outbox outbox = new Outbox(OUTBOX);
    private final Thread loop;

    private volatile boolean open = true;
    private volatile boolean connected;
    private volatile @Nullable Socket socket;

    /**
     * Connect in the background; {@link #healthy()} turns true once the broker accepted this server.
     *
     * @param secret this server's secret, as the broker wrote it for this server id
     */
    public DirectBus(String serverId, String host, int port, String secret, System.Logger log) {
        this.serverId = ServerId.check(Objects.requireNonNull(serverId, "serverId"));
        this.broker = InetSocketAddress.createUnresolved(Objects.requireNonNull(host, "host"), port);
        this.signer = new Signer(secret);
        this.log = Objects.requireNonNull(log, "log");
        this.handlers = new Handlers(log);
        this.loop = Thread.ofVirtual().name("uxmlib-network-" + serverId).start(this::run);
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public void publish(String topic, byte[] payload) {
        queue(new Frame(serverId, "", topic, payload));
    }

    @Override
    public void send(String server, String topic, byte[] payload) {
        queue(new Frame(serverId, server, topic, payload));
    }

    @Override
    public void subscribe(String topic, Consumer<Frame> handler) {
        handlers.add(Objects.requireNonNull(topic, "topic"), Objects.requireNonNull(handler, "handler"));
    }

    @Override
    public boolean healthy() {
        return connected;
    }

    @Override
    public void close() {
        open = false;
        closeSocket();
        loop.interrupt();
    }

    private void queue(Frame frame) {
        if (outbox.offer(FrameCodec.encode(frame)) && outbox.dropped() % 100 == 1) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The network link is down and its queue is full; " + outbox.dropped()
                            + " frames were dropped so far.");
        }
    }

    private void run() {
        Duration pause = FIRST_PAUSE;
        while (open) {
            try (Socket opened = new Socket()) {
                socket = opened;
                opened.connect(new InetSocketAddress(broker.getHostString(), broker.getPort()), 5_000);
                opened.setSoTimeout((int) Wire.READ_TIMEOUT.toMillis());
                opened.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(new BufferedInputStream(opened.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(opened.getOutputStream()));
                long[] sequences = handshake(in, out);
                connected = true;
                pause = FIRST_PAUSE;
                log.log(System.Logger.Level.INFO, "Connected to the network at " + broker + ".");
                Thread writer = Thread.ofVirtual().start(() -> write(out, sequences[0]));
                try {
                    read(in, sequences[1]);
                } finally {
                    writer.interrupt();
                }
            } catch (IOException | RuntimeException lost) {
                if (open) {
                    log.log(
                            System.Logger.Level.WARNING,
                            (connected ? "Lost" : "Could not reach") + " the network at " + broker + ": "
                                    + lost.getMessage() + ". Trying again in " + pause.toSeconds() + "s.");
                }
            } finally {
                connected = false;
                socket = null;
            }
            if (!sleep(pause)) {
                return;
            }
            pause = pause.multipliedBy(2).compareTo(LONGEST_PAUSE) > 0 ? LONGEST_PAUSE : pause.multipliedBy(2);
        }
    }

    /** Prove this server to the broker and the broker to this server; answers the up and down sequences. */
    private long[] handshake(DataInputStream in, DataOutputStream out) throws IOException {
        long serverNonce = Signer.newNonce();
        out.writeUTF(serverId);
        out.writeLong(serverNonce);
        out.flush();
        long brokerNonce = in.readLong();
        out.write(signer.sign(serverNonce, Wire.serverProof(serverId, brokerNonce)));
        out.flush();
        byte[] proof = in.readNBytes(Signer.LENGTH);
        if (!signer.verify(brokerNonce, Wire.brokerProof(serverNonce), proof)) {
            throw new IOException("the broker does not hold this server's secret");
        }
        return new long[] {brokerNonce + 1, serverNonce + 1};
    }

    private void write(DataOutputStream out, long start) {
        long sequence = start;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] body = outbox.next(Wire.HEARTBEAT.toMillis());
                byte[] sending = body == null ? new byte[0] : body;
                try {
                    Wire.write(out, signer, sequence++, sending);
                } catch (IOException failed) {
                    if (body != null) {
                        outbox.putBack(body);
                    }
                    closeSocket();
                    return;
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private void read(DataInputStream in, long start) throws IOException {
        long sequence = start;
        while (open) {
            Optional<byte[]> body = Wire.read(in, signer, sequence++);
            if (body.isEmpty()) {
                continue;
            }
            Frame frame;
            try {
                frame = FrameCodec.decode(body.get());
            } catch (IllegalArgumentException unreadable) {
                log.log(System.Logger.Level.WARNING, "Dropped a network frame this library cannot read.", unreadable);
                continue;
            }
            if (!frame.origin().equals(serverId)) {
                handlers.deliver(frame);
            }
        }
    }

    private void closeSocket() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Closing is the goal; a socket that fails to close is closed enough.
            }
        }
    }

    private static boolean sleep(Duration pause) {
        try {
            Thread.sleep(pause);
            return true;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
