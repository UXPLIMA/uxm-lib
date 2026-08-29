package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** What the client is allowed to add under a menu icon's lore, and what it keeps. */
class TooltipsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** The two Sirac read off the screen, and the one that must survive. */
    @Test
    void theVanillaSetCoversTheLinesAButtonShouldNotShow() {
        assertThat(Tooltips.VANILLA_COMPONENTS)
                .contains(DataComponentTypes.DYED_COLOR, DataComponentTypes.EQUIPPABLE)
                .doesNotContain(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
    }

    @Test
    void turningTheVanillaTooltipOffHidesThatSet() {
        ItemStack item = ItemBuilder.of(Material.LEATHER_CHESTPLATE)
                .vanillaTooltip(false)
                .build();

        assertThat(hidden(item)).isEqualTo(Tooltips.VANILLA_COMPONENTS);
    }

    @Test
    void turningItBackOnLetsTheClientSpeakAgain() {
        ItemStack item = ItemBuilder.of(Material.LEATHER_CHESTPLATE)
                .vanillaTooltip(false)
                .vanillaTooltip(true)
                .build();

        assertThat(hidden(item)).isEmpty();
    }

    /** A caller who wants the trim line back builds the set from ours rather than typing out the rest. */
    @Test
    void aCallerCanHideItsOwnSetInstead() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_CHESTPLATE)
                .hiddenComponents(Set.of(DataComponentTypes.DYED_COLOR))
                .build();

        assertThat(hidden(item)).containsExactly(DataComponentTypes.DYED_COLOR);
    }

    /** Silencing the client is no reason to hand back a tooltip somebody took away entirely. */
    @Test
    void aTooltipHiddenWholeStaysHiddenWhole() {
        ItemStack item = ItemBuilder.of(Material.LEATHER_CHESTPLATE)
                .hideTooltip(true)
                .vanillaTooltip(false)
                .build();

        TooltipDisplay display = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        assertThat(display).isNotNull();
        assertThat(display.hideTooltip()).isTrue();
    }

    private static Set<DataComponentType> hidden(ItemStack item) {
        TooltipDisplay display = item.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        return display == null ? Set.of() : display.hiddenComponents();
    }
}
