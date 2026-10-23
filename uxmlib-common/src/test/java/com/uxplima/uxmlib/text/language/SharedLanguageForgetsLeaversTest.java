package com.uxplima.uxmlib.text.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;

import org.junit.jupiter.api.Test;

/**
 * The answer a player's data gave is remembered so that a message drawn off the player's thread can use it. It
 * was remembered by id and never let go, so a server kept one entry for every player who ever joined, in every
 * plugin carrying this library. It is now held for as long as the player object it was read from lives, and a
 * player who left is not kept. Memory is the behaviour here, so the remembered answers are read directly.
 */
class SharedLanguageForgetsLeaversTest {

    private final AtomicBoolean onTheirThread = new AtomicBoolean(true);
    private final SharedLanguage shared =
            SharedLanguage.with(player -> onTheirThread.get(), (player, work) -> work.run());

    @Test
    void aPlayerWhoLeftIsNotRemembered() throws Exception {
        WeakReference<Player> gone = joinAndChoose();

        for (int attempt = 0; attempt < 50 && gone.get() != null; attempt++) {
            System.gc();
            Thread.sleep(20);
        }

        assertThat(gone.get()).as("the player object was collected").isNull();
        assertThat(remembered()).isEmpty();
    }

    @Test
    void aPlayerWhoIsHereIsStillAnsweredOffTheirThread() {
        Player here = player();
        shared.write(here, Locale.forLanguageTag("tr"));

        onTheirThread.set(false);

        assertThat(shared.read(here)).contains(new SharedLanguage.Mark(Locale.forLanguageTag("tr")));
    }

    private WeakReference<Player> joinAndChoose() {
        Player leaver = player();
        shared.write(leaver, Locale.forLanguageTag("tr"));
        return new WeakReference<>(leaver);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        return player;
    }

    private Map<?, ?> remembered() throws ReflectiveOperationException {
        Field seen = SharedLanguage.class.getDeclaredField("seen");
        seen.setAccessible(true);
        return (Map<?, ?>) seen.get(shared);
    }
}
