package com.uxplima.uxmlib.common;

import java.util.logging.Logger;

/**
 * Operator-facing diagnostics, as a port the library owns.
 *
 * <p>A library module must be able to say something to the operator without deciding how a plugin logs. Paper
 * hands every plugin a {@link Logger}, Velocity hands it an SLF4J logger, and a test wants neither, so the
 * modules take this seam and the host binds it once.
 *
 * <p>A message carries {@code {}} placeholders, SLF4J style, and the binding expands them. That keeps a call
 * cheap when the level is off, because the arguments are only rendered if the line is really written. The text
 * is a literal for the operator, not player-facing wording: it never goes through a message catalog.
 */
public interface Log {

    /** A binding that writes nothing. It is the default for a test, and for a caller that wants silence. */
    Log NONE = new Log() {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    /**
     * A binding that writes to {@code logger}, which is what a Bukkit plugin already holds
     * ({@code JavaPlugin.getLogger()}).
     *
     * <p>{@link #debug} maps onto {@link java.util.logging.Level#FINE}, so an operator turns it on through the
     * logging configuration the server already has rather than through a switch of ours.
     *
     * @param logger the logger to write to; never {@code null}
     * @return a binding over {@code logger}
     */
    static Log of(Logger logger) {
        return new JavaUtilLog(logger);
    }

    /** Routine progress an operator may want to see at default verbosity. */
    void info(String message, Object... args);

    /** A recoverable anomaly worth surfacing without failing the operation. */
    void warn(String message, Object... args);

    /** A failure; the throwable carries the cause for the operator's investigation. */
    void error(String message, Throwable cause);

    /** Verbose detail, off by default, enabled for troubleshooting. */
    void debug(String message, Object... args);
}
