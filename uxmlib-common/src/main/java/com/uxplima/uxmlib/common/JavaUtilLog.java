package com.uxplima.uxmlib.common;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link Log} binding over a {@link Logger}, the logger every Bukkit plugin is handed.
 *
 * <p>The level is tested before the message is built, so a suppressed debug line costs one comparison rather
 * than a string concatenation. {@link java.util.logging.Logger} has its own {@code {0}} formatting, which this
 * deliberately does not use: the port promises SLF4J style {@code {}} placeholders, and mixing the two would
 * make the same message read differently under two bindings.
 */
final class JavaUtilLog implements Log {

    private final Logger logger;

    JavaUtilLog(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void info(String message, Object... args) {
        write(Level.INFO, message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        write(Level.WARNING, message, args);
    }

    @Override
    public void error(String message, Throwable cause) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(cause, "cause");
        logger.log(Level.SEVERE, message, cause);
    }

    @Override
    public void debug(String message, Object... args) {
        write(Level.FINE, message, args);
    }

    private void write(Level level, String message, Object... args) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(args, "args");
        if (!logger.isLoggable(level)) {
            return;
        }
        logger.log(level, expand(message, args));
    }

    /**
     * Replaces each {@code {}} in {@code message} with the next argument, left to right. A surplus argument is
     * dropped and a surplus placeholder is left standing: a diagnostic line must print what it can rather than
     * throw, because the caller is usually already reporting a failure.
     */
    static String expand(String message, Object... args) {
        if (args.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16 * args.length);
        int from = 0;
        int next = 0;
        int at = message.indexOf("{}", from);
        while (at >= 0 && next < args.length) {
            out.append(message, from, at).append(args[next]);
            next++;
            from = at + 2;
            at = message.indexOf("{}", from);
        }
        return out.append(message, from, message.length()).toString();
    }
}
