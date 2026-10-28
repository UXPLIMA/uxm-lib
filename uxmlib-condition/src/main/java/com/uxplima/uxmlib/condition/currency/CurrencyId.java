package com.uxplima.uxmlib.condition.currency;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The name of a currency, as the operator writes it in {@code currencies.conf}.
 *
 * <p>It is an operator's word rather than one of ours, because the plugin holds several currencies at
 * once and the plugin must not decide which ones exist. The type refuses a name that a configuration
 * path or a database column could not carry unchanged.
 */
public record CurrencyId(String value) {

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_-]{1,32}$");

    public CurrencyId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        value = value.toLowerCase(Locale.ROOT);
        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException("currency id must match " + ALLOWED.pattern() + ", got: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
