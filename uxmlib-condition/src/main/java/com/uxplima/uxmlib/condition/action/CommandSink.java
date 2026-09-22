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

    /**
     * A sink that silently discards every command.
     *
     * <p>It was the default until 2026-09-22 and is not one now. A verb an operator wrote that silently
     * does not happen is the failure this estate keeps paying for: the file is right, the log is clean,
     * and the command never runs. Silence is still available, and it has to be asked for, which is what a
     * test that only wants the line reached does.
     */
    static CommandSink noop() {
        return commandLine -> {};
    }

    /**
     * The default: a sink that throws and names the seam to wire, the way the delay seam does.
     *
     * @param verb the action that asked, named as an operator writes it, so the message says what broke
     * @param seam the builder method that wires it
     */
    static CommandSink unwired(String verb, String seam) {
        return commandLine -> {
            throw new IllegalStateException("an action asked to run " + verb + " " + commandLine
                    + " and this ActionContext has no command sink wired. Call ActionContext.Builder." + seam
                    + "(...) with your dispatcher, or CommandSink.noop() if silence is what you meant.");
        };
    }
}
