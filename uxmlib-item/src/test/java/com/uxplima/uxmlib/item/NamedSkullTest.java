package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A head given by name is drawn without asking anyone but the server's cache: a Mojang account the server knows is
 * drawn as itself, any other name by the name alone, never by an offline-mode id Mojang does not know.
 */
class NamedSkullTest {

    /** An account Mojang made: a version 4 id. */
    private static final Account NOTCH = new Account(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "Notch");
    /** An offline-mode account: its id derived from its name, version 3. */
    private static final Account OFFLINE_STEVE =
            new Account(UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8)), "Steve");

    private final Map<String, Account> cache = new HashMap<>();
    private final List<String> asked = new ArrayList<>();
    private final List<String> drawn = new ArrayList<>();

    @Test
    void aMojangAccountTheServerKnowsIsDrawnAsItself() {
        cache.put("notch", NOTCH);

        draw("Notch");

        assertThat(drawn).containsExactly("account " + NOTCH.id());
    }

    @Test
    void aNameTheServerDoesNotKnowIsDrawnByTheNameAloneWithoutALookup() {
        draw("jeb_");

        assertThat(drawn).containsExactly("name jeb_");
        assertThat(asked).containsExactly("jeb_");
    }

    @Test
    void anOfflineModeAccountIsDrawnByItsNameNotByAnIdMojangDoesNotKnow() {
        cache.put("steve", OFFLINE_STEVE);

        draw("Steve");

        assertThat(drawn).containsExactly("name Steve");
    }

    private void draw(String name) {
        NamedSkull.draw(
                name,
                typed -> {
                    asked.add(typed);
                    return cache.get(typed.toLowerCase(java.util.Locale.ROOT));
                },
                Account::id,
                account -> drawn.add("account " + account.id()),
                typed -> drawn.add("name " + typed));
    }

    private record Account(UUID id, String name) {}
}
