package com.uxplima.uxmlib.item;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Who a head given by name is drawn as, chosen without a lookup that could wait on Mojang, since an item is built on
 * a server thread. The account the server already knows under the name is drawn as itself when Mojang made it (a
 * version 4 id). Any other name is handed to the client as a name alone, which draws the Mojang account of that name,
 * the one a menu author who wrote it meant: an offline-mode account the server cached (a version 3 id) is not drawn by
 * an id Mojang does not know.
 */
final class NamedSkull {

    private static final int MOJANG_UUID_VERSION = 4;

    private NamedSkull() {}

    /**
     * Draw the head named {@code name}.
     *
     * @param cached the account the server already knows under a name, without asking anyone
     * @param idOf the id of such an account
     * @param asAccount draws the head as that account
     * @param byName draws the head as the name alone
     * @param <O> the server's account type
     */
    static <O> void draw(
            String name,
            Function<String, @Nullable O> cached,
            Function<O, UUID> idOf,
            Consumer<O> asAccount,
            Consumer<String> byName) {
        Objects.requireNonNull(name, "name");
        O known = cached.apply(name);
        if (known != null && idOf.apply(known).version() == MOJANG_UUID_VERSION) {
            asAccount.accept(known);
            return;
        }
        byName.accept(name);
    }
}
