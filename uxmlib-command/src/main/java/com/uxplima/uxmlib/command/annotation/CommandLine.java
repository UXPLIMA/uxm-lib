package com.uxplima.uxmlib.command.annotation;

import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Every line the command layer says on its own behalf, as a catalog key.
 *
 * <p>The command layer produces text that a plugin's own catalog would not otherwise hold: the help page,
 * the rejection of a bad argument, the cooldown notice. Left alone that text is the English
 * {@link CommandMessages} defaults, painted in vanilla colours, which makes it the one screen in a plugin
 * that is not the plugin's. These keys put it back in the catalog: they are read by
 * {@link CommandMessages#fromCatalogue}, they are translated with everything else, and the palette owns
 * them.
 *
 * <p>Each path is the one the plugins already ship, so a language file written before this enum existed
 * keeps working, and each default names a style role rather than a colour, as every template does.
 *
 * @see CommandMessages#fromCatalogue
 */
public enum CommandLine implements MessageKey {

    /** A player-only command was run from the console. */
    PLAYER_ONLY("command.player-only", "<etag:'ERROR'> <body>Only a player can run this command."),

    /** An argument was rejected, with no reason to give. */
    INVALID_VALUE(
            "command.invalid-value",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>."),

    /** An argument was rejected and the resolver said why. */
    INVALID_VALUE_WHY(
            "command.invalid-value-why",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>: <subtext><reason>"),

    /** An argument was rejected because it is not one of a known set. */
    NOT_ONE_OF(
            "command.not-one-of",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>."
                    + " Try one of <value><allowed><body>."),

    /** An argument was rejected with a detail but no argument name. */
    INVALID_ARGUMENT("command.invalid-argument", "<etag:'ERROR'> <body><detail>"),

    /** An argument was rejected with nothing to say about it. */
    BAD_ARGUMENT("command.bad-argument", "<etag:'ERROR'> <body>That argument is not valid."),

    /** The handler threw. */
    INTERNAL_ERROR(
            "command.internal-error",
            "<etag:'ERROR'> <body>Something went wrong while running this command." + " The console holds the reason."),

    /** The sender must wait before running this again. */
    ON_COOLDOWN("command.on-cooldown", "<etag:'ERROR'> <body>Wait <value><time><body> before you use this again."),

    /** The first line of a help page. */
    HELP_HEADER(
            "command.help-header", "<h:'Help'> <dim>/<value><command> <dim>(<value><page><dim>/<value><pages><dim>)"),

    /** The command part of a help line. */
    HELP_COMMAND("command.help-command", "<value><command>"),

    /** What stands between a command and its description. */
    HELP_SEPARATOR("command.help-separator", "<dim> : "),

    /** The description part of a help line. */
    HELP_DESCRIPTION("command.help-description", "<subtext><description>"),

    /** The hover on a help line, which writes the command into the chat box when clicked. */
    HELP_FILL_HINT("command.help-fill-hint", "<subtext>Click to write this command."),

    /** The hover on a page button. */
    HELP_PAGE_HINT("command.help-page-hint", "<subtext>Page <value><page>");

    private final String path;
    private final String defaultTemplate;

    CommandLine(String path, String defaultTemplate) {
        this.path = path;
        this.defaultTemplate = defaultTemplate;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }
}
