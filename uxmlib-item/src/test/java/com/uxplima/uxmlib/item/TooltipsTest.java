package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentBuilder;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

/**
 * What the client is allowed to add under a menu icon's lore.
 *
 * <p>The component is read back off a mocked stack rather than off a built one: MockBukkit accepts a data
 * component and does not keep it, so a round trip through a real {@link ItemStack} proves nothing here.
 */
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
    void hidingTheVanillaSetPutsExactlyThatSetOnTheItem() {
        ItemStack item = mock(ItemStack.class);

        Tooltips.hide(item, Tooltips.VANILLA_COMPONENTS);

        assertThat(applied(item).hiddenComponents()).isEqualTo(Tooltips.VANILLA_COMPONENTS);
    }

    /** A caller who wants the trim line back builds the set from ours rather than typing out the rest. */
    @Test
    void aCallerCanHideItsOwnSetInstead() {
        ItemStack item = mock(ItemStack.class);

        Tooltips.hide(item, Set.of(DataComponentTypes.DYED_COLOR));

        assertThat(applied(item).hiddenComponents()).containsExactly(DataComponentTypes.DYED_COLOR);
    }

    @Test
    void anEmptySetLetsTheClientSpeakAgain() {
        ItemStack item = mock(ItemStack.class);

        Tooltips.hide(item, Set.of());

        assertThat(applied(item).hiddenComponents()).isEmpty();
    }

    /** Silencing the client is no reason to hand back a tooltip somebody took away entirely. */
    @Test
    void aTooltipHiddenWholeStaysHiddenWhole() {
        ItemStack item = mock(ItemStack.class);
        when(item.getData(DataComponentTypes.TOOLTIP_DISPLAY))
                .thenReturn(TooltipDisplay.tooltipDisplay().hideTooltip(true).build());

        Tooltips.hide(item, Tooltips.VANILLA_COMPONENTS);

        assertThat(applied(item).hideTooltip()).isTrue();
    }

    @Test
    void theBuilderRoutesBothWaysThrough() {
        assertThatCode(() -> ItemBuilder.of(Material.LEATHER_CHESTPLATE)
                        .vanillaTooltip(false)
                        .hiddenComponents(Set.of(DataComponentTypes.TRIM))
                        .vanillaTooltip(true)
                        .build())
                .doesNotThrowAnyException();
    }

    /** The tooltip display this call put on {@code item}. */
    private static TooltipDisplay applied(ItemStack item) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DataComponentBuilder<TooltipDisplay>> captor =
                ArgumentCaptor.forClass(DataComponentBuilder.class);
        verify(item).setData(eq(DataComponentTypes.TOOLTIP_DISPLAY), captor.capture());
        return captor.getValue().build();
    }
}
