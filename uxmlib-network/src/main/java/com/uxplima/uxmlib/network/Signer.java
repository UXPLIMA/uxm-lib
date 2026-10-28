package com.uxplima.uxmlib.network;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs and checks the frames of one connection with one server's secret.
 *
 * <p>Every frame is signed over its sequence number and its bytes. The sequence starts at a random value
 * both sides agree on in the handshake and goes up by one per frame, so a frame recorded off the wire and
 * sent again, or sent to another connection, fails its check.
 */
public final class Signer {

    /** The length of a signature in bytes. */
    public static final int LENGTH = 32;

    private static final String ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;
    /** A Mac per thread, made once: looking one up per frame costs a provider search each time. */
    private final ThreadLocal<Mac> macs = ThreadLocal.withInitial(this::newMac);

    public Signer(String secret) {
        Objects.requireNonNull(secret, "secret");
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalArgumentException("a network secret is base64", notBase64);
        }
        if (raw.length < 32) {
            throw new IllegalArgumentException("a network secret holds at least 32 bytes");
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    /** A new secret: 32 random bytes in base64, what a proxy writes for each server. */
    public static String newSecret() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    /** A random starting point for a connection's sequence. */
    public static long newNonce() {
        return RANDOM.nextLong();
    }

    public byte[] sign(long sequence, byte[] body) {
        Mac mac = macs.get();
        mac.update(ByteBuffer.allocate(Long.BYTES).putLong(sequence).array());
        return mac.doFinal(body);
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac;
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("this Java has no " + ALGORITHM, missing);
        }
    }

    /** Whether {@code signature} is this secret's signature of {@code body} at {@code sequence}. */
    public boolean verify(long sequence, byte[] body, byte[] signature) {
        return MessageDigest.isEqual(sign(sequence, body), signature);
    }
}
