package com.uxplima.uxmlib.condition.currency;

import java.util.List;
import java.util.Optional;

/**
 * Every currency the operator turned on.
 *
 * <p>The set comes from {@code currencies.conf} and never from the code. A server that wants one currency
 * deletes the rest, and a server that wants seven writes seven, and nothing in the plugin notices either
 * way.
 */
public interface Currencies {

    /** The currency under that id, when it exists and is ready. */
    Optional<Currency> byId(CurrencyId id);

    /** Every currency that can be used right now, in the order the file names them. */
    List<Currency> ready();

    /**
     * The currency a price is written in when nobody said which.
     *
     * <p>There is always one. An operator who turned every currency off gets the first of the file back,
     * ready or not, because a plugin with no currency cannot price anything and the console must say so
     * rather than the plugin guessing.
     */
    Currency preferred();
}
