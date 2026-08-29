package com.uxplima.uxmlib.text.style;

import java.util.Objects;

/**
 * Writes ASCII letters as the small capitals a stylised interface is set in.
 *
 * <p>Only the twenty-six Latin letters map. A digit, a punctuation mark and every other character come back
 * as they are, because a player has to read a number and a glyph has no small-capital form. Two letters are
 * special: {@code x} has no small capital in Unicode and stays as it is, and {@code q} becomes {@code ǫ},
 * which is the shape the Unicode block offers.
 *
 * <p>Convert a template, not a player's own words: {@link Typography} is the pass that knows which parts of a
 * template are text and which are tags.
 */
public final class SmallCaps {

    private static final String ASCII = "abcdefghijklmnopqrstuvwxyz";

    private static final String[] SMALL = {
        "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ", "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ",
        "ᴡ", "x", "ʏ", "ᴢ"
    };

    private SmallCaps() {}

    /** {@code text} with every ASCII letter in its small-capital form. */
    public static String of(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            out.append(of(text.charAt(index)));
        }
        return out.toString();
    }

    /** One character, converted when it is an ASCII letter and returned as it is when it is not. */
    public static String of(char character) {
        int index = ASCII.indexOf(Character.toLowerCase(character));
        return index >= 0 && isAscii(character) ? SMALL[index] : String.valueOf(character);
    }

    private static boolean isAscii(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }
}
