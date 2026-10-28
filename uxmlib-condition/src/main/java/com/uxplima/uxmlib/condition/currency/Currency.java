package com.uxplima.uxmlib.condition.currency;

import java.util.UUID;

/**
 * One way to pay.
 *
 * <p>The plugin holds several at once, and it owns none of them. Two are its own: experience, and an item
 * such as a diamond. The rest are bridges to an economy plugin that a server may or may not have, and a
 * bridge whose plugin is absent is a currency that is simply not ready. Nothing fails to start over one.
 *
 * <p>Every method takes a player id rather than a player, because the seller of a listing is usually
 * offline when it sells. A currency that cannot pay an absent player says so, and the plugin writes the
 * money into their mailbox instead.
 */
public interface Currency {

    CurrencyId id();

    /** Whether this currency can be used right now. A bridge with no plugin behind it is not ready. */
    boolean isReady();

    /** Whether an absent player can be paid directly. Experience cannot; a balance in a database can. */
    boolean paysOffline();

    /**
     * Whether this currency counts in whole numbers only.
     *
     * <p>Experience and points do. A price of half a point cannot be paid, and a plugin that accepted one
     * would take money it could not give back, so the plugin refuses the price rather than rounding it.
     */
    boolean isWhole();

    /** What one player has. */
    Money balanceOf(UUID player);

    /**
     * Take {@code amount} from {@code player}, and say whether it happened.
     *
     * <p>False means nothing moved: not enough money, or the player is not reachable. It is never a
     * partial take.
     */
    boolean take(UUID player, Money amount);

    /** Give {@code amount} to {@code player}, and say whether it happened. */
    boolean give(UUID player, Money amount);
}
