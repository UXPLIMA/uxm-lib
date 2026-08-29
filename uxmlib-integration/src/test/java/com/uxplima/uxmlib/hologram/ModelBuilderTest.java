package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pure tests of the item/block builder's accumulated {@link Appearance}: live spawning is integration-only
 * (MockBukkit can build the item/block content but not spawn an {@code ItemDisplay}/{@code BlockDisplay}). The
 * shared {@link Holograms.ModelBuilder} carries only the {@link Display}-wide fields (billboard, glow, view
 * range, brightness, scale, rotation, transform); the text-only fields have no builder method here, so this
 * pins that what an operator configures on an item or block hologram round-trips through {@code appearance()}.
 */
class ModelBuilderTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void itemBuilderDefaultsToCenterBillboardAndNoOverrides() {
        Appearance look = Holograms.item(new ItemStack(Material.DIAMOND)).appearance();

        assertThat(look.billboard()).isEqualTo(Display.Billboard.CENTER);
        assertThat(look.glow()).isNull();
        assertThat(look.viewRange()).isNull();
        assertThat(look.transform()).isNull();
    }

    @Test
    void itemBuilderAccumulatesTheSharedDisplayFields() {
        Appearance look = Holograms.item(new ItemStack(Material.DIAMOND))
                .billboard(Display.Billboard.FIXED)
                .glow(Color.RED)
                .viewRange(2.0f)
                .brightness(new Display.Brightness(15, 7))
                .scale(2.0f)
                .rotation(90f)
                .appearance();

        assertThat(look.billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(look.glow()).isEqualTo(Color.RED);
        assertThat(look.viewRange()).isEqualTo(2.0f);
        assertThat(java.util.Objects.requireNonNull(look.brightness()).getBlockLight())
                .isEqualTo(15);
        Transform transform = look.transform();
        assertThat(transform).isNotNull();
        assertThat(java.util.Objects.requireNonNull(transform).scaleX()).isEqualTo(2.0f);
        assertThat(java.util.Objects.requireNonNull(transform).yawDegrees()).isEqualTo(90f);
    }

    @Test
    void blockBuilderAccumulatesTheSharedDisplayFields() {
        Appearance look = Holograms.block(Material.OAK_LOG.createBlockData())
                .billboard(Display.Billboard.VERTICAL)
                .scale(1.5f)
                .appearance();

        assertThat(look.billboard()).isEqualTo(Display.Billboard.VERTICAL);
        assertThat(java.util.Objects.requireNonNull(look.transform()).scaleX()).isEqualTo(1.5f);
    }

    @Test
    void modelBuilderCarriesTranslationPerAxisScaleAndShadow() {
        Appearance look = Holograms.item(new ItemStack(Material.DIAMOND))
                .translation(0f, 0.75f, 0f)
                .scale(1f, 2f, 1f)
                .shadowRadius(2f)
                .shadowStrength(0.5f)
                .appearance();

        assertThat(look.shadowRadius()).isEqualTo(2f);
        assertThat(look.shadowStrength()).isEqualTo(0.5f);
        Transform transform = java.util.Objects.requireNonNull(look.transform());
        assertThat(transform.transY()).isEqualTo(0.75f);
        assertThat(transform.scaleY()).isEqualTo(2f);
    }
}
