package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The whole appearance table, read out of one block an operator wrote.
 *
 * <p>The canon says every property in that table is a key an operator can set and that a plugin writing its
 * own reader for them is the defect the rule exists for. uxmCrates was found drawing its own text with two
 * properties, lines and a height, while the table lists thirteen. This is the reader that closes that, so it
 * is written once and every plugin spells the same words.
 *
 * <p>Two rules matter more than the list. A key nobody wrote keeps the vanilla value rather than a magic
 * number, and a key an operator mistyped is loud: silence there is a file somebody edits over and over
 * wondering why nothing changes.
 */
class HologramAppearanceConfigTest {

    private static ConfigurationNode node(String hocon) throws ConfigurateException {
        return HoconConfigurationLoader.builder().buildAndLoadString(hocon);
    }

    @Test
    @DisplayName("a block that is not there is the plain look")
    void anAbsentBlockIsTheDefault() throws ConfigurateException {
        assertThat(HologramAppearanceConfig.load(node("").node("appearance"))).isEqualTo(HologramAppearance.defaults());
    }

    @Test
    @DisplayName("every key of the appearance table reaches the look")
    void thewholeTableIsRead() throws ConfigurateException {
        HologramAppearance look = HologramAppearanceConfig.load(node("""
                        billboard        = "vertical"
                        alignment        = "left"
                        text-shadow      = true
                        see-through      = true
                        background       = "#80102030"
                        text-opacity     = 128
                        glow             = "#ff5555"
                        brightness-block = 15
                        brightness-sky   = 7
                        line-width       = 320
                        line-gap         = 0.4
                        view-range       = 2.5
                        shadow-radius    = 1.5
                        shadow-strength  = 0.75
                        scale            = 1.5
                        offset { x = 0.1, y = 2.0, z = -0.1 }
                        """));

        assertThat(look.billboard()).isEqualTo(Display.Billboard.VERTICAL);
        assertThat(look.alignment()).isEqualTo(TextDisplay.TextAlignment.LEFT);
        assertThat(look.textShadow()).isTrue();
        assertThat(look.seeThrough()).isTrue();
        assertThat(look.background()).isEqualTo(Color.fromARGB(0x80, 0x10, 0x20, 0x30));
        assertThat(look.textOpacity()).isEqualTo(128);
        assertThat(look.glowArgb())
                .isEqualTo(Color.fromARGB(0xff, 0xff, 0x55, 0x55).asARGB());
        assertThat(look.brightnessBlock()).isEqualTo(15);
        assertThat(look.brightnessSky()).isEqualTo(7);
        assertThat(look.lineWidth()).isEqualTo(320);
        assertThat(look.lineGap()).isCloseTo(0.4f, within(1e-6f));
        assertThat(look.viewRange()).isCloseTo(2.5f, within(1e-6f));
        assertThat(look.shadowRadius()).isCloseTo(1.5f, within(1e-6f));
        assertThat(look.shadowStrength()).isCloseTo(0.75f, within(1e-6f));
        assertThat(look.scale().x()).isCloseTo(1.5f, within(1e-6f));
        assertThat(look.translation().y()).isCloseTo(2.0f, within(1e-6f));
    }

    /** A stored hologram that was never styled reads back as vanilla, never as a number somebody has to know. */
    @Test
    @DisplayName("a key nobody wrote keeps the vanilla value")
    void anunwrittenKeyIsLeftAlone() throws ConfigurateException {
        HologramAppearance look = HologramAppearanceConfig.load(node("line-width = 300"));

        assertThat(look.lineWidth()).isEqualTo(300);
        assertThat(look.hasGlow()).isFalse();
        assertThat(look.hasBrightness()).isFalse();
        assertThat(look.hasShadow()).isFalse();
        assertThat(look.billboard()).isEqualTo(HologramAppearance.defaults().billboard());
    }

    @Test
    @DisplayName("the word for nothing turns a colour off rather than painting it black")
    void noneIsNotBlack() throws ConfigurateException {
        HologramAppearance look = HologramAppearanceConfig.load(node("glow = \"none\"\nbackground = \"none\""));

        assertThat(look.hasGlow()).isFalse();
        assertThat(look.background()).isEqualTo(HologramAppearance.NO_BACKGROUND);
    }

    @Test
    @DisplayName("a colour written without an alpha is opaque")
    void asixDigitColourIsOpaque() throws ConfigurateException {
        assertThat(HologramAppearanceConfig.load(node("background = \"#102030\""))
                        .background())
                .isEqualTo(Color.fromARGB(0xff, 0x10, 0x20, 0x30));
    }

    @Test
    @DisplayName("a value that names no colour, no billboard and no alignment says which word was wrong")
    void amistypedValueIsLoud() throws ConfigurateException {
        assertThatThrownBy(() -> HologramAppearanceConfig.load(node("glow = \"#gggggg\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("#gggggg");
        assertThatThrownBy(() -> HologramAppearanceConfig.load(node("billboard = \"sideways\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sideways");
        assertThatThrownBy(() -> HologramAppearanceConfig.load(node("alignment = \"justified\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justified");
    }

    /** An operator writes one number for a hologram that is simply bigger, and three for one that is stretched. */
    @Test
    @DisplayName("scale is read as one number or as three")
    void scaleIsOneNumberOrThree() throws ConfigurateException {
        assertThat(HologramAppearanceConfig.load(node("scale = 2.0")).scale())
                .satisfies(scale -> assertThat(scale.x()).isCloseTo(2.0f, within(1e-6f)))
                .satisfies(scale -> assertThat(scale.z()).isCloseTo(2.0f, within(1e-6f)));

        assertThat(HologramAppearanceConfig.load(node("scale { x = 1, y = 3, z = 1 }"))
                        .scale()
                        .y())
                .isCloseTo(3.0f, within(1e-6f));
    }

    /** A light of sixteen is not a light. A value out of range is the operator's typo and it says so. */
    @Test
    @DisplayName("a brightness outside the light range is refused")
    void abrightnessOutOfRangeIsRefused() throws ConfigurateException {
        assertThatThrownBy(() -> HologramAppearanceConfig.load(node("brightness-block = 16")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brightnessBlock");
    }
}
