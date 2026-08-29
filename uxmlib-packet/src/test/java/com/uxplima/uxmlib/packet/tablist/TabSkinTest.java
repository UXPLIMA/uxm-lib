package com.uxplima.uxmlib.packet.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Value-semantics of the {@link TabSkin} record: construction, the unsigned factory, the value guard, and the
 * default-skin contract a skinless fake player relies on. A fake player spawned through the add-entity packet on
 * 1.20.2+ links its body to its player-info entry's profile, and clients drop a profile carrying no
 * {@code textures} property, so an NPC with no skin must still ship one. {@link TabSkin#orDefault} supplies
 * {@link TabSkin#DEFAULT} (the classic Steve texture) for exactly that case.
 */
class TabSkinTest {

    @Test
    void carriesValueAndSignature() {
        TabSkin skin = new TabSkin("base64value", "sig");

        assertThat(skin.textureValue()).isEqualTo("base64value");
        assertThat(skin.signature()).isEqualTo("sig");
    }

    @Test
    void unsignedHasNoSignature() {
        TabSkin skin = TabSkin.unsigned("base64value");

        assertThat(skin.textureValue()).isEqualTo("base64value");
        assertThat(skin.signature()).isNull();
    }

    @Test
    void rejectsNullTextureValue() {
        assertThatNullPointerException().isThrownBy(() -> new TabSkin(nullString(), "sig"));
    }

    @Test
    void orDefaultKeepsAPresentSkin() {
        TabSkin own = new TabSkin("ownvalue", "sig");

        assertThat(TabSkin.orDefault(own)).isSameAs(own);
    }

    @Test
    void orDefaultSuppliesTheDefaultForANullSkin() {
        // The skinless case: a player-info ADD must still carry a texture, so null falls back to the Steve default.
        assertThat(TabSkin.orDefault(null)).isSameAs(TabSkin.DEFAULT);
    }

    @Test
    void theDefaultIsAnUnsignedWellFormedTexturesPayload() {
        // The default ships unsigned (a synthetic profile cannot bear Mojang's signature) and must decode to a
        // textures JSON pointing at a real skin URL, or the client renders nothing.
        assertThat(TabSkin.DEFAULT.signature()).isNull();

        String json = new String(Base64.getDecoder().decode(TabSkin.DEFAULT.textureValue()), StandardCharsets.UTF_8);

        assertThat(json).contains("\"SKIN\"").contains("http://textures.minecraft.net/texture/");
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the constructor guard fires.
    private static String nullString() {
        return null;
    }
}
