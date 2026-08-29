package com.uxplima.uxmlib.storage.repository;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates SQL identifiers (table and column names) the library inlines into statements. Values always
 * go through bound {@code ?} placeholders, but identifiers cannot be parameters, so they are checked
 * against a strict allowlist (a bare name or one dotted qualifier), and anything else is rejected, so a
 * column name threaded from untrusted input can never inject SQL.
 */
final class Identifiers {

    private static final Pattern VALID = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private Identifiers() {}

    /** Return {@code value} if it is a simple SQL identifier, else throw. */
    static String require(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value
                    + "'); write the statement by hand for anything else");
        }
        return value;
    }
}
