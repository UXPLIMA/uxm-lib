package com.uxplima.uxmlib.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A form button shows the picture Bedrock's own pack draws the item with. */
class BedrockIconsTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static BedrockImage path(String path) {
        return new BedrockImage(BedrockImage.Kind.PATH, path);
    }

    @Test
    void anItemBedrockNamesDifferentlyGetsBedrocksTexture() {
        assertThat(BedrockIcons.forMaterialSpec("GOLDEN_APPLE", VIEWER)).isEqualTo(path("textures/items/apple_golden"));
        assertThat(BedrockIcons.forMaterialSpec("minecraft:golden_apple", VIEWER))
                .isEqualTo(path("textures/items/apple_golden"));
        assertThat(BedrockIcons.forMaterialSpec("RED_BED", VIEWER)).isEqualTo(path("textures/items/bed_red"));
        assertThat(BedrockIcons.forMaterialSpec("LIGHT_GRAY_STAINED_GLASS", VIEWER))
                .isEqualTo(path("textures/blocks/glass_silver"));
        assertThat(BedrockIcons.forMaterialSpec("STONE", VIEWER)).isEqualTo(path("textures/blocks/stone"));
    }

    @Test
    void anItemBedrockDrawsAsAnEntityHasNoPictureRatherThanTheMissingOne() {
        assertThat(BedrockIcons.forMaterialSpec("WHITE_BANNER", VIEWER)).isNull();
        assertThat(BedrockIcons.forMaterialSpec("PLAYER_HEAD", VIEWER)).isNull();
        assertThat(BedrockIcons.forMaterialSpec("AIR", VIEWER)).isNull();
        assertThat(BedrockIcons.forMaterialSpec(" ", VIEWER)).isNull();
    }

    @Test
    void anItemNewerThanTheTableIsGuessed() {
        assertThat(BedrockIcons.forMaterialSpec("SHINY_THING", VIEWER)).isEqualTo(path("textures/items/shiny_thing"));
        assertThat(BedrockIcons.forMaterialSpec("GLOWING_BLOCK", VIEWER))
                .isEqualTo(path("textures/blocks/glowing_block"));
        assertThat(BedrockIcons.forMaterialSpec("YETI_SPAWN_EGG", VIEWER))
                .isEqualTo(path("textures/items/spawn_eggs/spawn_egg_yeti"));
    }

    @Test
    void aHeadIsTheAvatarOfItsPlayer() {
        assertThat(BedrockIcons.forMaterialSpec("skull:self", VIEWER))
                .isEqualTo(new BedrockImage(BedrockImage.Kind.URL, "https://mc-heads.net/avatar/" + VIEWER));
        assertThat(BedrockIcons.forMaterialSpec("head:Notch", VIEWER))
                .isEqualTo(new BedrockImage(BedrockImage.Kind.URL, "https://mc-heads.net/avatar/Notch"));
        assertThat(BedrockIcons.forMaterialSpec("basehead:abc", VIEWER)).isNull();
    }
}
