package com.uxplima.uxmlib.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * The byte layout of a direct connection, shared by both ends.
 *
 * <p>The handshake proves both ends hold the server's secret without sending it: the server names itself
 * and a nonce, the broker answers with its own nonce, and each signs the other's nonce. Frames that follow
 * are a length, the frame's bytes and a signature over a per-direction sequence that starts at the nonce
 * the other end chose, so a recorded session cannot be replayed into a new one. A body of length zero is a
 * heartbeat that keeps an idle connection's read timeout from firing.
 */
final class Wire {

    static final Duration HEARTBEAT = Duration.ofSeconds(15);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(45);

    private Wire() {}

    /** The bytes a server's handshake proof signs: its id and the broker's nonce. */
    static byte[] serverProof(String serverId, long brokerNonce) {
        byte[] id = serverId.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(id.length + Long.BYTES)
                .put(id)
                .putLong(brokerNonce)
                .array();
    }

    /** The bytes the broker's handshake proof signs: the server's nonce. */
    static byte[] brokerProof(long serverNonce) {
        return ByteBuffer.allocate(Long.BYTES).putLong(serverNonce).array();
    }

    static void write(DataOutputStream out, Signer signer, long sequence, byte[] body) throws IOException {
        out.writeInt(body.length);
        out.write(body);
        out.write(signer.sign(sequence, body));
        out.flush();
    }

    /**
     * The next body, empty for a heartbeat.
     *
     * @throws IOException when the stream ends, a length is out of range or a signature fails: the
     *     connection is no longer trusted and is closed by the caller
     */
    static Optional<byte[]> read(DataInputStream in, Signer signer, long sequence) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > FrameCodec.MAX_PAYLOAD + 512) {
            throw new IOException("frame length out of range: " + length);
        }
        byte[] body = in.readNBytes(length);
        byte[] signature = in.readNBytes(Signer.LENGTH);
        if (body.length != length || signature.length != Signer.LENGTH) {
            throw new IOException("connection closed inside a frame");
        }
        if (!signer.verify(sequence, body, signature)) {
            throw new IOException("a frame failed its signature");
        }
        return length == 0 ? Optional.empty() : Optional.of(body);
    }
}
