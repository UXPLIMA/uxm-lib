package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Pure tests of the skin-texture base64 round-trip: no server, no network. */
class SkinTexturesTest {

    private static final String SKIN_URL = "http://textures.minecraft.net/texture/abc123";

    @Test
    void encodesAUrlIntoTheTexturesEnvelope() {
        String base64 = SkinTextures.encode(SKIN_URL);
        String json = new String(Base64.getDecoder().decode(base64), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("\"textures\"").contains("\"SKIN\"").contains(SKIN_URL);
    }

    @Test
    void decodesTheUrlBackOutOfTheEnvelope() {
        String base64 = SkinTextures.encode(SKIN_URL);
        assertThat(SkinTextures.decodeUrl(base64)).contains(SKIN_URL);
    }

    @Test
    void roundTripsAnHttpsUrl() {
        String url = "https://textures.minecraft.net/texture/deadbeef";
        assertThat(SkinTextures.decodeUrl(SkinTextures.encode(url))).contains(url);
    }

    @Test
    void returnsEmptyForAblobWithoutASkinUrl() {
        // The empty envelope {"textures":{}} carries no SKIN url.
        String empty = Base64.getEncoder()
                .encodeToString("{\"textures\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(SkinTextures.decodeUrl(empty)).isEmpty();
    }

    @Test
    void returnsEmptyForGarbageBase64() {
        assertThat(SkinTextures.decodeUrl("not-valid-base64-@@@")).isEmpty();
    }

    @Test
    void returnsEmptyForBase64ThatIsNotJson() {
        String notJson =
                Base64.getEncoder().encodeToString("hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(SkinTextures.decodeUrl(notJson)).isEmpty();
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullUrlOnEncode() {
        assertThatThrownBy(() -> SkinTextures.encode(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankUrlOnEncode() {
        assertThatThrownBy(() -> SkinTextures.encode("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullBlobOnDecode() {
        assertThatThrownBy(() -> SkinTextures.decodeUrl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void decodeUrlIsTolerantOfSurroundingWhitespaceAndEscapes() {
        // Mojang serves the url with escaped slashes inside the JSON; both forms must decode.
        String escaped = Base64.getEncoder()
                .encodeToString(
                        "{\"textures\":{\"SKIN\":{\"url\":\"http:\\/\\/textures.minecraft.net\\/texture\\/x\"}}}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Optional<String> url = SkinTextures.decodeUrl(escaped);
        assertThat(url).contains("http://textures.minecraft.net/texture/x");
    }
}
