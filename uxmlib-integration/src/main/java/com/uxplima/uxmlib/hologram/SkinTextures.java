package com.uxplima.uxmlib.hologram;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts between a skin's {@code textures.minecraft.net} URL and the base64 {@code textures} property
 * value a {@code PLAYER_HEAD} profile carries. The property payload is the base64 of a small JSON
 * envelope ({@code {"textures":{"SKIN":{"url":"…"}}}}), so a head that already has a base64 blob (from
 * Mojang or a cache) needs no further round-trip, and a head built from a bare URL is wrapped here.
 *
 * <p>Pure and offline: no server, no network. The decode side is forgiving: a malformed blob, a non-JSON
 * payload, or an envelope without a {@code SKIN} URL all yield {@link Optional#empty()} rather than throw,
 * because the input often comes from an external source that may have changed shape.
 */
final class SkinTextures {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private SkinTextures() {}

    /** Wrap a skin URL into the base64 {@code textures} property value a profile expects. */
    static String encode(String skinUrl) {
        Objects.requireNonNull(skinUrl, "skinUrl");
        if (skinUrl.isBlank()) {
            throw new IllegalArgumentException("skinUrl must not be blank");
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
        return ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Pull the {@code SKIN} URL back out of a base64 {@code textures} value, or empty if the blob is not
     * valid base64, is not the expected JSON, or carries no skin URL. Escaped slashes ({@code \/}) that
     * Mojang emits are normalised to plain slashes.
     */
    static Optional<String> decodeUrl(String base64) {
        Objects.requireNonNull(base64, "base64");
        String json = decodeJson(base64);
        if (json == null) {
            return Optional.empty();
        }
        return extractSkinUrl(json);
    }

    private static @org.jspecify.annotations.Nullable String decodeJson(String base64) {
        try {
            return new String(DECODER.decode(base64.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    // A deliberately small extractor: find the SKIN object, then the first "url" string inside it. This
    // avoids pulling in a JSON library for a payload whose shape Mojang has kept stable for years.
    private static Optional<String> extractSkinUrl(String json) {
        int skin = json.indexOf("\"SKIN\"");
        if (skin < 0) {
            return Optional.empty();
        }
        int urlKey = json.indexOf("\"url\"", skin);
        if (urlKey < 0) {
            return Optional.empty();
        }
        int open = json.indexOf('"', json.indexOf(':', urlKey) + 1);
        if (open < 0) {
            return Optional.empty();
        }
        int close = json.indexOf('"', open + 1);
        if (close < 0) {
            return Optional.empty();
        }
        return Optional.of(json.substring(open + 1, close).replace("\\/", "/"));
    }
}
