package com.uxplima.uxmlib.text;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pure sanitizer that removes stale legacy colour / format codes a
 * MiniMessage renderer would not interpret. Applied at the inbound chat
 * boundary (chat listener + shortcut command) BEFORE
 * {@link ColorCapabilityStripper} so a player can't smuggle a {@code §c}
 * past the capability gate, and so a literal {@code &c} never shows up in
 * chat (MiniMessage leaves it as literal text).
 *
 * <p>The pattern matches {@code &} or the legacy section sign
 * ({@code U+00A7}) <em>immediately</em> followed by a legacy code character
 * ({@code 0-9}, {@code a-f}, {@code k-o}, {@code r}, {@code x}:
 * case-insensitive). A bare {@code &} followed by a space or any non-code
 * character (e.g. {@code "Tom & Jerry"}) is left untouched.
 *
 * <p>The section sign is written as the {@code \\u00a7} escape rather than
 * the literal glyph so the {@code LegacyChatApiDriftTest} guard, which bans
 * a raw {@code U+00A7} byte in production source, stays green; the compiled
 * pattern is byte-identical to one written with the literal character.
 */
public final class LegacyCodeSanitizer {

    private LegacyCodeSanitizer() {}

    private static final Pattern LEGACY = Pattern.compile("[&\\u00a7][0-9A-Fa-fK-Ok-oRrXx]");

    /** Remove leftover legacy colour / format codes. */
    public static String strip(String text) {
        Objects.requireNonNull(text, "text");
        return LEGACY.matcher(text).replaceAll("");
    }
}
