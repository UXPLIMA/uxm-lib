package com.uxplima.uxmlib.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The hub of a direct network, run by the proxy.
 *
 * <p>Each server connects once and proves it holds the secret written for its id. The broker relays a
 * broadcast to every other connected server and a targeted frame to its one target. A frame that claims an
 * origin other than the server that sent it is dropped: one server's secret never speaks for another.
 *
 * <p>The broker is a {@link Bus} itself under the id {@value #PROXY}, so the proxy plugin publishes and
 * subscribes like any server.
 *
 * <p>Each connection is served by platform threads (see {@link Wire#thread}), so a connection that has not proved
 * its secret yet is bounded twice: at most {@value #MAX_HANDSHAKES} are served at once, and each has
 * {@link #HANDSHAKE_TIMEOUT} to finish. Whoever reaches the port can therefore neither start threads without end
 * nor keep the servers out by holding every place open.
 */
public final class DirectBroker implements Bus {

    /** The id the broker speaks under. */
    public static final String PROXY = "proxy";

    /** How many connections may be proving their secret at the same time; one past it is closed at once. */
    static final int MAX_HANDSHAKES = 32;

    /** How long a connection has to prove its secret before it is closed. */
    static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);

    private static final long REFUSAL_LOG_GAP_MILLIS = Duration.ofMinutes(1).toMillis();

    private final InetSocketAddress bind;
    private final Map<String, Signer> signers;
    private final System.Logger log;
    private final Handlers handlers;
    private final Map<String, Link> links = new ConcurrentHashMap<>();
    private final ServerSocket listening;
    private final Thread acceptor;
    private final int maxHandshakes;
    private final Duration handshakeTimeout;
    private final Semaphore handshakes;
    private final AtomicLong lastRefusalLog = new AtomicLong(Long.MIN_VALUE);
    private volatile boolean open = true;

    /**
     * Start listening.
     *
     * @param secrets every server id this broker accepts, with the secret written for it
     * @throws IOException when the address cannot be bound
     */
    public DirectBroker(String host, int port, Map<String, String> secrets, System.Logger log) throws IOException {
        this(host, port, secrets, log, MAX_HANDSHAKES, HANDSHAKE_TIMEOUT);
    }

    /** Start listening, with the handshake limits a test needs. */
    DirectBroker(
            String host,
            int port,
            Map<String, String> secrets,
            System.Logger log,
            int maxHandshakes,
            Duration handshakeTimeout)
            throws IOException {
        Objects.requireNonNull(secrets, "secrets");
        if (maxHandshakes < 1) {
            throw new IllegalArgumentException("maxHandshakes must be at least 1");
        }
        this.maxHandshakes = maxHandshakes;
        this.handshakeTimeout = Objects.requireNonNull(handshakeTimeout, "handshakeTimeout");
        this.handshakes = new Semaphore(maxHandshakes);
        this.log = Objects.requireNonNull(log, "log");
        this.handlers = new Handlers(log);
        Map<String, Signer> built = new ConcurrentHashMap<>();
        secrets.forEach((id, secret) -> {
            String checked = ServerId.check(id);
            if (checked.equals(PROXY)) {
                // The broker's own id: a server under it could speak as the proxy and never hear its frames.
                log.log(
                        System.Logger.Level.WARNING,
                        "A server cannot be called " + PROXY + ", the broker's own id; it is left off the network.");
                return;
            }
            built.put(checked, new Signer(secret));
        });
        this.signers = Map.copyOf(built);
        this.bind = new InetSocketAddress(Objects.requireNonNull(host, "host"), port);
        this.listening = new ServerSocket();
        try {
            listening.bind(bind);
        } catch (IOException taken) {
            listening.close();
            throw taken;
        }
        this.acceptor = Wire.thread("uxmlib-network-broker", this::accept);
    }

    /** The servers connected right now. */
    public Set<String> connected() {
        return Set.copyOf(links.keySet());
    }

    /** The port the broker listens on; differs from the one asked for when that was zero. */
    public int port() {
        return listening.getLocalPort();
    }

    @Override
    public String serverId() {
        return PROXY;
    }

    @Override
    public void publish(String topic, byte[] payload) {
        route(new Frame(PROXY, "", topic, payload));
    }

    @Override
    public void send(String server, String topic, byte[] payload) {
        route(new Frame(PROXY, server, topic, payload));
    }

    @Override
    public void subscribe(String topic, Consumer<Frame> handler) {
        handlers.add(Objects.requireNonNull(topic, "topic"), Objects.requireNonNull(handler, "handler"));
    }

    @Override
    public boolean healthy() {
        return open && !listening.isClosed();
    }

    @Override
    public void close() {
        open = false;
        try {
            listening.close();
        } catch (IOException ignored) {
            // The listener is going away either way.
        }
        acceptor.interrupt();
        links.values().forEach(Link::close);
        links.clear();
    }

    private void accept() {
        while (open) {
            try {
                Socket socket = listening.accept();
                if (!handshakes.tryAcquire()) {
                    refuse(socket);
                    continue;
                }
                Wire.thread("uxmlib-network-broker-reader", () -> serve(socket));
            } catch (IOException stopped) {
                if (open) {
                    log.log(
                            System.Logger.Level.WARNING,
                            "The network broker stopped accepting: " + stopped.getMessage());
                }
                return;
            }
        }
    }

    /** Close a connection that came while every handshake place was taken, and say so at most once a minute. */
    private void refuse(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // It is refused either way.
        }
        long now = System.currentTimeMillis();
        long last = lastRefusalLog.get();
        if ((last == Long.MIN_VALUE || now - last >= REFUSAL_LOG_GAP_MILLIS)
                && lastRefusalLog.compareAndSet(last, now)) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The network broker refused a connection from " + socket.getRemoteSocketAddress() + ": "
                            + maxHandshakes + " connections are already proving their secret.");
        }
    }

    private void serve(Socket socket) {
        String id = "?";
        Link link = null;
        AtomicBoolean proving = new AtomicBoolean(true);
        // A virtual thread only sleeps here; it touches no socket, so the Windows wakeup bug cannot reach it.
        Thread deadline = Thread.ofVirtual().start(() -> closeIfStillProving(socket, proving));
        try (socket) {
            socket.setSoTimeout((int) Wire.READ_TIMEOUT.toMillis());
            socket.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            String claimed = in.readUTF();
            long serverNonce = in.readLong();
            if (!ServerId.isValid(claimed)) {
                log.log(
                        System.Logger.Level.WARNING,
                        "A connection from " + socket.getRemoteSocketAddress()
                                + " named itself with an id no server can have.");
                return;
            }
            id = claimed;
            Signer signer = signers.get(id);
            if (signer == null) {
                log.log(
                        System.Logger.Level.WARNING,
                        "A server named " + id + " tried to join the network from " + socket.getRemoteSocketAddress()
                                + "; it has no secret here.");
                return;
            }
            long brokerNonce = Signer.newNonce();
            out.writeLong(brokerNonce);
            out.flush();
            byte[] proof = in.readNBytes(Signer.LENGTH);
            if (!signer.verify(serverNonce, Wire.serverProof(id, brokerNonce), proof)) {
                log.log(
                        System.Logger.Level.WARNING,
                        "A server claiming to be " + id + " from " + socket.getRemoteSocketAddress()
                                + " does not hold its secret.");
                return;
            }
            out.write(signer.sign(brokerNonce, Wire.brokerProof(serverNonce)));
            out.flush();
            proven(proving, deadline);
            link = new Link(id, socket, out, signer, serverNonce + 1);
            Link replaced = links.put(id, link);
            if (replaced != null) {
                replaced.close();
            }
            log.log(System.Logger.Level.INFO, "The server " + id + " joined the network.");
            Thread writer = Wire.thread("uxmlib-network-broker-" + id + "-writer", link::write);
            try {
                read(id, in, signer, brokerNonce + 1);
            } finally {
                writer.interrupt();
            }
        } catch (IOException | RuntimeException lost) {
            if (open && link != null) {
                log.log(System.Logger.Level.INFO, "The server " + id + " left the network: " + lost.getMessage());
            }
        } finally {
            proven(proving, deadline);
            if (link != null) {
                links.remove(id, link);
            }
        }
    }

    /**
     * Close {@code socket} when it has not proved its secret within the handshake timeout. Whichever of this and
     * {@link #proven} takes {@code proving} first gives the place back, so it is given back once.
     */
    private void closeIfStillProving(Socket socket, AtomicBoolean proving) {
        try {
            Thread.sleep(handshakeTimeout);
        } catch (InterruptedException done) {
            return;
        }
        if (proving.compareAndSet(true, false)) {
            handshakes.release();
            try {
                socket.close();
            } catch (IOException ignored) {
                // The handshake fails either way.
            }
        }
    }

    /** The connection no longer holds a handshake place: it proved its secret, or it is gone. */
    private void proven(AtomicBoolean proving, Thread deadline) {
        if (proving.compareAndSet(true, false)) {
            deadline.interrupt();
            handshakes.release();
        }
    }

    private void read(String id, DataInputStream in, Signer signer, long start) throws IOException {
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
                log.log(System.Logger.Level.WARNING, "Dropped a frame from " + id + " this broker cannot read.");
                continue;
            }
            if (!frame.origin().equals(id)) {
                log.log(
                        System.Logger.Level.WARNING,
                        "Dropped a frame from " + id + " that claims to come from " + frame.origin() + ".");
                continue;
            }
            route(frame);
        }
    }

    private void route(Frame frame) {
        byte[] body = FrameCodec.encode(frame);
        if (frame.isBroadcast()) {
            links.forEach((id, link) -> {
                if (!id.equals(frame.origin())) {
                    link.send(body);
                }
            });
            if (!frame.origin().equals(PROXY)) {
                handlers.deliver(frame);
            }
            return;
        }
        if (frame.target().equals(PROXY)) {
            if (!frame.origin().equals(PROXY)) {
                handlers.deliver(frame);
            }
            return;
        }
        Link target = links.get(frame.target());
        if (target != null) {
            target.send(body);
        }
    }

    /**
     * One connected server: its socket, its queue and its down sequence.
     *
     * <p>Routing only queues. Each link writes on its own thread, so a server that reads slowly delays
     * nobody but itself, and one that falls a full queue behind is cut off rather than allowed to hold
     * the frames of every other server in memory.
     */
    private final class Link {

        private static final int QUEUE = 4096;
        private static final long QUEUE_BYTES = 16L << 20;

        private final String id;
        private final Socket socket;
        private final DataOutputStream out;
        private final Signer signer;
        private final Outbox queue = new Outbox(QUEUE, QUEUE_BYTES);
        private long sequence;

        Link(String id, Socket socket, DataOutputStream out, Signer signer, long start) {
            this.id = id;
            this.socket = socket;
            this.out = out;
            this.signer = signer;
            this.sequence = start;
        }

        void send(byte[] body) {
            if (queue.offer(body)) {
                log.log(
                        System.Logger.Level.WARNING,
                        "The server " + id + " reads too slowly to keep up and was"
                                + " disconnected; it will connect again.");
                close();
            }
        }

        void write() {
            try {
                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                    byte[] body = queue.next(Wire.HEARTBEAT.toMillis());
                    Wire.write(out, signer, sequence++, body == null ? new byte[0] : body);
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (IOException failed) {
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing is the goal.
            }
        }
    }
}
