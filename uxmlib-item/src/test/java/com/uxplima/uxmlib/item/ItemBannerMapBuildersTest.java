package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ItemBannerMapBuildersTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void addsABannerPatternFromColourAndType() {
        ItemStack banner = ItemBuilder.of(Material.WHITE_BANNER)
                .bannerPattern(DyeColor.RED, PatternType.CROSS)
                .build();

        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        assertThat(meta.getPatterns()).hasSize(1);
        assertThat(meta.getPattern(0).getColor()).isEqualTo(DyeColor.RED);
        assertThat(meta.getPattern(0).getPattern()).isEqualTo(PatternType.CROSS);
    }

    @Test
    void addsAPreBuiltBannerPattern() {
        Pattern pattern = new Pattern(DyeColor.BLUE, PatternType.BORDER);
        ItemStack banner =
                ItemBuilder.of(Material.WHITE_BANNER).bannerPattern(pattern).build();

        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        assertThat(meta.getPatterns()).containsExactly(pattern);
    }

    @Test
    void replacesAllBannerPatterns() {
        Pattern first = new Pattern(DyeColor.GREEN, PatternType.STRIPE_LEFT);
        Pattern second = new Pattern(DyeColor.YELLOW, PatternType.STRIPE_RIGHT);
        ItemStack banner = ItemBuilder.of(Material.WHITE_BANNER)
                .bannerPattern(DyeColor.RED, PatternType.CROSS)
                .bannerPatterns(java.util.List.of(first, second))
                .build();

        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        assertThat(meta.getPatterns()).containsExactly(first, second);
    }

    @Test
    void bannerPatternsIsANoOpOnANonBanner() {
        ItemStack stone = ItemBuilder.of(Material.STONE)
                .bannerPattern(DyeColor.RED, PatternType.CROSS)
                .build();

        assertThat(stone.getType()).isEqualTo(Material.STONE);
        assertThat(stone.getItemMeta()).isNotInstanceOf(BannerMeta.class);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void rejectsNullBannerInputs() {
        assertThatThrownBy(() -> ItemBuilder.of(Material.WHITE_BANNER).bannerPattern(null, PatternType.CROSS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.WHITE_BANNER).bannerPattern(DyeColor.RED, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.WHITE_BANNER).bannerPattern(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.WHITE_BANNER).bannerPatterns(null))
                .isInstanceOf(NullPointerException.class);
    }

    // The map colour API is deprecated on 26.3, where the game no longer has a map colour: see ItemMetaSupport.
    @SuppressWarnings("deprecation")
    @Test
    void setsMapColourAndScaling() {
        ItemStack map = ItemBuilder.of(Material.FILLED_MAP)
                .mapColor(Color.fromRGB(0x336699))
                .mapScaling(true)
                .build();

        MapMeta meta = (MapMeta) map.getItemMeta();
        assertThat(meta.getColor()).isEqualTo(Color.fromRGB(0x336699));
        assertThat(meta.isScaling()).isTrue();
    }

    @Test
    void bindsAMapView() {
        MapView view = server.createMap(server.addSimpleWorld("maps"));
        ItemStack map = ItemBuilder.of(Material.FILLED_MAP).mapView(view).build();

        MapMeta meta = (MapMeta) map.getItemMeta();
        assertThat(meta.getMapView()).isEqualTo(view);
    }

    @Test
    void mapSettersAreANoOpOnANonMap() {
        ItemStack stone = ItemBuilder.of(Material.STONE)
                .mapColor(Color.RED)
                .mapScaling(true)
                .build();

        assertThat(stone.getType()).isEqualTo(Material.STONE);
        assertThat(stone.getItemMeta()).isNotInstanceOf(MapMeta.class);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void rejectsNullMapInputs() {
        assertThatThrownBy(() -> ItemBuilder.of(Material.FILLED_MAP).mapColor(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.FILLED_MAP).mapLocationName(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.FILLED_MAP).mapView(null))
                .isInstanceOf(NullPointerException.class);
    }
}
