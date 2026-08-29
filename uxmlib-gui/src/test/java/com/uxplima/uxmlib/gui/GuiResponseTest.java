package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.item.GuiItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The declarative click result is side-effects-as-data: a handler returns a {@link GuiResponse} list and
 * the framework applies it. These tests build responses with the factories and assert they carry the data
 * they were given: application is covered separately against a fake applier. MockBukkit is here only so
 * the {@link ItemStack} fixtures can be built.
 */
class GuiResponseTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void nothingIsTheSharedNoOp() {
        assertThat(GuiResponse.nothing()).isSameAs(GuiResponse.nothing());
        assertThat(GuiResponse.nothing()).isInstanceOf(GuiResponse.Nothing.class);
    }

    @Test
    void closeIsTheSharedSingleton() {
        assertThat(GuiResponse.close()).isSameAs(GuiResponse.close());
        assertThat(GuiResponse.close()).isInstanceOf(GuiResponse.Close.class);
    }

    @Test
    void refreshIsTheSharedSingleton() {
        assertThat(GuiResponse.refresh()).isSameAs(GuiResponse.refresh());
    }

    @Test
    void openCarriesTheTargetMenu() {
        SimpleGui target = Guis.gui().rows(1).build();

        GuiResponse.Open open = (GuiResponse.Open) GuiResponse.open(target);

        assertThat(open.gui()).isSameAs(target);
    }

    @Test
    void updateItemCarriesSlotAndItem() {
        GuiItem item = GuiItem.display(new ItemStack(Material.STONE));

        GuiResponse.UpdateItem update = (GuiResponse.UpdateItem) GuiResponse.updateItem(5, item);

        assertThat(update.slot()).isEqualTo(5);
        assertThat(update.item()).isSameAs(item);
    }

    @Test
    void playSoundCarriesTheSound() {
        Sound sound = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 1f, 1f);

        GuiResponse.PlaySound play = (GuiResponse.PlaySound) GuiResponse.playSound(sound);

        assertThat(play.sound()).isSameAs(sound);
    }

    @Test
    void replaceCursorCarriesTheItem() {
        ItemStack cursor = new ItemStack(Material.EMERALD, 4);

        GuiResponse.ReplaceCursor replace = (GuiResponse.ReplaceCursor) GuiResponse.replaceCursor(cursor);

        assertThat(replace.cursor().getType()).isEqualTo(Material.EMERALD);
    }

    @Test
    void runCarriesTheRunnable() {
        Runnable r = () -> {};

        GuiResponse.Run run = (GuiResponse.Run) GuiResponse.run(r);

        assertThat(run.task()).isSameAs(r);
    }

    @Test
    void factoriesAreSyncSugarThatWrapInACompletedFuture() {
        List<GuiResponse> responses = List.of(GuiResponse.close(), GuiResponse.refresh());

        var future = GuiResponse.completed(responses);

        assertThat(future.isDone()).isTrue();
        assertThat(future.join()).isEqualTo(responses);
    }

    @Test
    void updateItemRejectsANegativeSlot() {
        assertThatThrownBy(() -> GuiResponse.updateItem(-1, GuiItem.display(new ItemStack(Material.STONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
