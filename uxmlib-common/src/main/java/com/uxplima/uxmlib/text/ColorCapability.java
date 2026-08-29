package com.uxplima.uxmlib.text;

/**
 * Categorisation of MiniMessage tag classes for permission-gated colour
 * stripping (P14 #23). A player's set of capabilities decides which tag
 * classes survive the {@code ColorCapabilityStripper} pass on the inbound
 * boundary; disallowed tags are removed from the raw chat body before any
 * downstream processing (moderation pipeline, format substitution,
 * cross-server replication) sees the message.
 *
 * <p>Categories:
 * <ul>
 *   <li>{@link #BASIC}: named colours: {@code <red>}, {@code <blue>},
 *       {@code <gold>}, {@code <color:NAME>}, etc.</li>
 *   <li>{@link #HEX}: direct hex literals: {@code <#ff8800>},
 *       {@code <color:#ff8800>}.</li>
 *   <li>{@link #GRADIENT}: {@code <gradient:...>}.</li>
 *   <li>{@link #RAINBOW}: {@code <rainbow>} and its phase variants.</li>
 *   <li>{@link #FORMATTING}: text decorations: {@code <bold>}, {@code <italic>},
 *       {@code <underlined>}, {@code <strikethrough>}, {@code <obfuscated>}
 *       and their short aliases ({@code <b>}, {@code <i>}, {@code <u>},
 *       {@code <st>}, {@code <obf>}).</li>
 * </ul>
 *
 * <p>Closing tags ({@code </red>}, etc.) are stripped together with their
 * opening counterparts so the rendered output stays well-formed.
 *
 * <p>Out-of-scope tags (placeholders, click/hover events, reset, newline)
 * are left intact regardless of capabilities: they're either
 * structurally needed or controlled by a separate permission domain.
 */
public enum ColorCapability {
    BASIC,
    HEX,
    GRADIENT,
    RAINBOW,
    FORMATTING
}
