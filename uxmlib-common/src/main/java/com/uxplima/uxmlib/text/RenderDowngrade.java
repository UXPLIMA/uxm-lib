package com.uxplima.uxmlib.text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure render-downgrade transforms. Given a MiniMessage source
 * string and the recipient's {@link ClientCapabilities}, rewrite the tags that
 * the recipient cannot render into the closest thing they can:
 *
 * <ul>
 *   <li>{@code <gradient:a:b:…>…</gradient>} → {@code <a>…</a>}: flatten to the
 *       first colour stop (Bedrock has no gradient).</li>
 *   <li>{@code <font:…>…</font>} → the inner content only: drop the custom-font
 *       wrapper (Bedrock ignores custom fonts).</li>
 *   <li>{@code <hover:show_item:'key':n>} → {@code <hover:show_text:'key xn'>}:
 *       fall back to a textual tooltip on protocol versions that cannot render
 *       a {@code show_item} hover.</li>
 * </ul>
 *
 * <p>Downgrade is silent: the message always delivers; only the unsupported
 * flourish is degraded. String-level, no Adventure: the result is a
 * MiniMessage source string the sink parses. Capabilities the recipient has are
 * left completely untouched (the matching pattern is skipped), so a fully
 * capable client receives the input verbatim.
 */
public final class RenderDowngrade {

    private RenderDowngrade() {}

    /**
     * Matches a gradient open ({@code <gradient:…>} / {@code <gr:…>}, group 1 =
     * the args after the head) OR a gradient close ({@code </gradient>} /
     * {@code </gr>}, group 2 non-null). One pattern so a single forward scan can
     * pair opens with closes via a colour stack.
     */
    private static final Pattern GRADIENT = Pattern.compile("<(?:gradient|gr):([^<>]+)>|(</(?:gradient|gr)>)");

    /** {@code <font:…>} and {@code </font>}. */
    private static final Pattern FONT_TAG = Pattern.compile("</?font(?::[^<>]+)?>");

    /**
     * {@code <hover:show_item:'KEY'[:AMOUNT]>}. Group 1 = the quoted key body,
     * group 2 = the optional numeric amount.
     */
    private static final Pattern SHOW_ITEM = Pattern.compile("<hover:show_item:'([^']*)'(?::(\\d+))?>");

    /** A MiniMessage gradient phase arg is a (possibly signed/decimal) number, not a colour. */
    private static final Pattern PHASE_ARG = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * Apply every downgrade the recipient's {@code caps} require. Returns
     * {@code text} verbatim when {@code caps} permits everything.
     */
    public static String apply(String text, ClientCapabilities caps) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(caps, "caps");
        String result = text;
        if (!caps.gradient()) {
            result = flattenGradients(result);
        }
        if (!caps.customFont()) {
            result = dropCustomFonts(result);
        }
        if (!caps.hoverItem()) {
            result = showItemToShowText(result);
        }
        return result;
    }

    /**
     * Single forward scan: each {@code <gradient:a:b:…>} becomes {@code <a>}
     * (first colour stop, pushed on a stack) and each {@code </gradient>}
     * becomes {@code </a>} popped from the stack. Gradients do not legally nest
     * in MiniMessage, so the stack rarely exceeds depth 1, but it keeps mixed
     * sequences well-formed. An unbalanced close (no matching open) falls back
     * to {@code </white>} rather than emitting a dangling gradient close.
     */
    private static String flattenGradients(String text) {
        Matcher m = GRADIENT.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        Deque<String> colours = new ArrayDeque<>();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            last = m.end();
            if (m.group(2) != null) {
                String colour = colours.isEmpty() ? "white" : colours.pop();
                out.append("</").append(colour).append('>');
            } else {
                String first = firstColourStop(m.group(1));
                colours.push(first);
                out.append('<').append(first).append('>');
            }
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private static String firstColourStop(String args) {
        // args looks like "red:blue" or "#ff0000:#00ff00:#0000ff" or
        // "red:blue:0.5" (trailing phase). The first colon-separated token is
        // normally the first colour stop. Defensive: if it is a phase number,
        // skip to the next non-numeric token, falling back to "white".
        String[] parts = args.split(":", -1);
        if (!PHASE_ARG.matcher(parts[0]).matches() && !parts[0].isEmpty()) {
            return parts[0];
        }
        for (String p : parts) {
            if (!p.isEmpty() && !PHASE_ARG.matcher(p).matches()) {
                return p;
            }
        }
        return "white";
    }

    /** Strip every {@code <font:…>} / {@code </font>} marker, keeping inner content. */
    private static String dropCustomFonts(String text) {
        return FONT_TAG.matcher(text).replaceAll("");
    }

    /**
     * Rewrite each {@code <hover:show_item:'key'[:amount]>} into
     * {@code <hover:show_text:'key[ xamount]'>}. The closing {@code </hover>}
     * (when present) is unchanged: both hover variants close the same way.
     */
    private static String showItemToShowText(String text) {
        Matcher m = SHOW_ITEM.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (m.find()) {
            String key = m.group(1);
            String amount = m.group(2);
            String tooltip = amount == null ? key : key + " x" + amount;
            m.appendReplacement(out, Matcher.quoteReplacement("<hover:show_text:'" + tooltip + "'>"));
        }
        m.appendTail(out);
        return out.toString();
    }
}
