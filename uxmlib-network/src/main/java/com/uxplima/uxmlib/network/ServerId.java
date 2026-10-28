package com.uxplima.uxmlib.network;

import java.util.regex.Pattern;

/** The rule a server id keeps: it names a server in a config file, a log line and a frame. */
final class ServerId {

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private ServerId() {}

    static boolean isValid(String id) {
        return ALLOWED.matcher(id).matches();
    }

    static String check(String id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("a server id must match " + ALLOWED.pattern() + ", got: " + id);
        }
        return id;
    }
}
