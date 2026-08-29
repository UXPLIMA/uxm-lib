package com.uxplima.uxmlib.condition.action;

/**
 * The seam through which a command action dispatches a (placeholder-resolved) command line. Production wiring
 * passes a sink backed by {@code Bukkit#dispatchCommand}, for a {@code [console]} action with the console
 * sender, for a {@code [player]} action with the target player. Tests pass a capturing sink so the {@code
 * Bukkit} static stays out of pure parser tests while the resolved command line is still asserted.
 *
 * <p>Keeping the contract here (rather than reaching into Bukkit from the action closure) means a command
 * action is the same pure closure in a test and in production; only the sink differs.
 */
@FunctionalInterface
public interface CommandSink {

    /** Dispatch one fully-resolved command line. The leading slash, if any, has already been stripped. */
    void dispatch(String commandLine);

    /** A sink that silently discards every command: the default when no command target is wired. */
    static CommandSink noop() {
        return commandLine -> {};
    }
}
