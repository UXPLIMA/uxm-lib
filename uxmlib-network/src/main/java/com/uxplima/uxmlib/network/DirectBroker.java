package com.uxplima.uxmlib.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 */
public final class DirectBroker implements Bus {

    /** The id the broker speaks under. */
    public static final String PROXY = "proxy";

    private final InetSocketAddress bind;
    private final Map<String, Signer> signers;
    private final System.Logger log;
    private final Handlers handlers;
    private final Map<String, Link> links = new ConcurrentHashMap<>();
    private final ServerSocket listening;
    private final Thread acceptor;
    private volatile boolean open = true;

    /**
     * Start listening.
     *
     * @param secrets every server id this broker accepts, with the secret written for it
     * @throws IOException when the address cannot be bound
     */
    public DirectBroker(String host, int port, Map<String, String> secrets, System.Logger log) throws IOException {
        Objects.requireNonNull(secrets, "secrets");
        this.log = Objects.requireNonNull(log, "log");
        this.handlers = new Handlers(log);
        Map<String, Signer> built = new ConcurrentHashMap<>();
        secrets.forEach((id, secret) -> built.put(ServerId.check(id), new Signer(secret)));
        this.signers = Map.copyOf(built);
        this.bind = new InetSocketAddress(Objects.requireNonNull(host, "host"), port);
        this.listening = new ServerSocket();
        listening.bind(bind);
        this.acceptor = Thread.ofVirtual().name("uxmlib-network-broker").start(this::accept);
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
                Thread.ofVirtual().start(() -> serve(socket));
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

    private void serve(Socket socket) {
        String id = "?";
        Link link = null;
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
            link = new Link(id, socket, out, signer, serverNonce + 1);
            Link replaced = links.put(id, link);
            if (replaced != null) {
                replaced.close();
            }
            log.log(System.Logger.Level.INFO, "The server " + id + " joined the network.");
            Thread writer = Thread.ofVirtual().start(link::write);
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
            if (link != null) {
                links.remove(id, link);
            }
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
            handlers.deliver(frame);
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

        private final String id;
        private final Socket socket;
        private final DataOutputStream out;
        private final Signer signer;
        private final Outbox queue = new Outbox(QUEUE);
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
