package com.uxplima.uxmlib.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The bytes of one frame, the same on every transport.
 *
 * <p>A version byte leads, so a server running a newer library can refuse what it cannot read instead of
 * misreading it. The payload is capped: a frame is a message, not a file, and a cap keeps one bad sender
 * from making every reader allocate without limit.
 */
public final class FrameCodec {

    /** The largest payload a frame carries. */
    public static final int MAX_PAYLOAD = 1 << 20;

    private static final byte VERSION = 1;

    private FrameCodec() {}

    public static byte[] encode(Frame frame) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 + frame.payload().length);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] payload = frame.payload();
            if (payload.length > MAX_PAYLOAD) {
                throw new IllegalArgumentException(
                        "a payload is at most " + MAX_PAYLOAD + " bytes, got: " + payload.length);
            }
            out.writeByte(VERSION);
            out.writeUTF(frame.origin());
            out.writeUTF(frame.target());
            out.writeUTF(frame.topic());
            out.writeInt(payload.length);
            out.write(payload);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /** The frame these bytes hold; refuses anything it cannot read in full. */
    public static Frame decode(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unknown frame version " + version);
            }
            String origin = in.readUTF();
            String target = in.readUTF();
            String topic = in.readUTF();
            int length = in.readInt();
            if (length < 0 || length > MAX_PAYLOAD) {
                throw new IllegalArgumentException("payload length out of range: " + length);
            }
            byte[] payload = in.readNBytes(length);
            if (payload.length != length || in.available() > 0) {
                throw new IllegalArgumentException("frame length does not match its bytes");
            }
            return new Frame(origin, target, topic, payload);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("truncated frame", truncated);
        }
    }
}
